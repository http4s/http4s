/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package staticcontent

import cats.MonadThrow
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.syntax.all._
import fs2.io.file.Files
import fs2.io.file.NoSuchFileException
import fs2.io.file.Path
import org.http4s.headers.Range.SubRange
import org.http4s.headers._
import org.http4s.server.middleware.TranslateUri
import org.typelevel.ci._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen

import scala.util.control.NoStackTrace

object FileService {

  type PathCollector[F[_]] = (Path, Config[F], Request[F]) => OptionT[F, Response[F]]

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param pathPrefix prefix of Uri from which content will be served
    * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param bufferSize buffer size to use for internal read buffers
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](
      systemPath: String,
      pathCollector: PathCollector[F],
      pathPrefix: String,
      bufferSize: Int,
      cacheStrategy: CacheStrategy[F],
  )

  object Config {
    def apply[F[_]: MonadThrow: Files: LoggerFactoryGen](
        systemPath: String,
        pathPrefix: String = "",
        bufferSize: Int = 50 * 1024,
        cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
    ): Config[F] = {
      val pathCollector: PathCollector[F] = (p, c, r) => filesOnly(p, c, r)
      Config(systemPath, pathCollector, pathPrefix, bufferSize, cacheStrategy)
    }

  }

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  private[staticcontent] def apply[F[_]: Files: LoggerFactoryGen](
      config: Config[F]
  )(implicit F: Concurrent[F]): HttpRoutes[F] = {
    implicit val logger: Logger[F] = LoggerFactory.getLogger[F]
    object BadTraversal extends Exception with NoStackTrace
    def withPath(rootPath: Path)(request: Request[F]): OptionT[F, Response[F]] = {
      val resolvePath: F[Path] =
        if (request.pathInfo.isEmpty) F.pure(rootPath)
        else {
          val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
          F.catchNonFatal {
            segments.foldLeft(rootPath) {
              case (_, "" | "." | "..") => throw BadTraversal
              case (path, segment) => path.resolve(segment)
            }
          }
        }

      val matchingPath: F[Option[Path]] =
        for {
          path <- resolvePath
          existsPath <- Files[F].exists(path, followLinks = false)
        } yield
          if (existsPath && path.startsWith(rootPath))
            Some(path.absolute.normalize)
          else None

      OptionT(matchingPath)
        .flatMap(path => config.pathCollector(path, config, request))
        .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
        .recoverWith { case BadTraversal => OptionT.some(Response(Status.BadRequest)) }
    }

    val readPath: F[Path] = Files[F].realPath(Path(config.systemPath))
    val inner: F[HttpRoutes[F]] = readPath.attempt.flatMap {
      case Right(rootPath) =>
        TranslateUri(config.pathPrefix)(Kleisli(withPath(rootPath))).pure

      case Left(_: NoSuchFileException) =>
        logger
          .error(
            s"Could not find root path from FileService config: systemPath = ${config.systemPath}, pathPrefix = ${config.pathPrefix}. All requests will return none."
          )
          .as(HttpRoutes.empty)

      case Left(e) =>
        logger
          .error(e)(
            s"Could not resolve root path from FileService config: systemPath = ${config.systemPath}, pathPrefix = ${config.pathPrefix}. All requests will fail with a 500."
          )
          .as(HttpRoutes.pure(Response(Status.InternalServerError)))
    }

    Kleisli((_: Any) => OptionT.liftF(inner)).flatten
  }

  private def filesOnly[F[_]: Files: LoggerFactoryGen](path: Path, config: Config[F], req: Request[F])(
      implicit F: MonadThrow[F]
  ): OptionT[F, Response[F]] =
    OptionT(Files[F].getBasicFileAttributes(path).flatMap { attr =>
      if (attr.isDirectory)
        StaticFile
          .fromPath(path / "index.html", Some(req))
          .value
      else if (!attr.isRegularFile) F.pure(None)
      else
        OptionT(getPartialContentFile(path, config, req))
          .orElse(
            StaticFile
              .fromPath(path, config.bufferSize, Some(req), StaticFile.calculateETag)
              .map(_.putHeaders(AcceptRangeHeader))
          )
          .value
    })

  private def validRange(start: Long, end: Option[Long], fileLength: Long): Boolean =
    start < fileLength && (end match {
      case Some(end) => start >= 0 && start <= end
      case None => start >= 0 || fileLength + start - 1 >= 0
    })

  // Attempt to find a Range header and collect only the subrange of content requested
  private def getPartialContentFile[F[_]: Files: LoggerFactoryGen](
      file: Path,
      config: Config[F],
      req: Request[F],
  )(implicit
      F: MonadThrow[F]
  ): F[Option[Response[F]]] =
    Files[F].getBasicFileAttributes(file).flatMap { attr =>
      def nope: F[Option[Response[F]]] =
        Some(
          Response[F](
            status = Status.RangeNotSatisfiable,
            headers = Headers
              .apply(
                AcceptRangeHeader,
                `Content-Range`(SubRange(0, attr.size - 1), Some(attr.size)),
              ),
          )
        ).pure[F].widen

      req.headers.get[Range] match {
        case Some(Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil))) =>
          if (validRange(s, e, attr.size)) {
            val size = attr.size
            val start = if (s >= 0) s else math.max(0, size + s)
            val end = math.min(size - 1, e.getOrElse(size - 1)) // end is inclusive

            StaticFile
              .fromPath(
                file,
                start,
                end + 1,
                config.bufferSize,
                Some(req),
                StaticFile.calculateETag,
              )
              .map { resp =>
                val hs = resp.headers
                  .put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
                resp.copy(status = Status.PartialContent, headers = hs)
              }
              .value
          } else nope
        case _ =>
          req.headers.get(ci"Range") match {
            case Some(_) =>
              // It exists, but it didn't parse
              nope
            case None =>
              F.pure(None)
          }
      }
    }
}
