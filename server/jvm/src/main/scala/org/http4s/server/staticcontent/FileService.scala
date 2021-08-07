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

import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.kernel.Async
import cats.syntax.all._
import java.io.File
import java.nio.file.{Files, LinkOption, NoSuchFileException, Path, Paths}
import org.http4s.headers.Range.SubRange
import org.http4s.headers._
import org.http4s.server.middleware.TranslateUri
import org.log4s.getLogger
import org.typelevel.ci._
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

object FileService {
  private[this] val logger = getLogger

  type PathCollector[F[_]] = (File, Config[F], Request[F]) => OptionT[F, Response[F]]

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param pathPrefix prefix of Uri from which content will be served
    * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param bufferSize buffer size to use for internal read buffers
    * @param blocker to use for blocking I/O
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](
      systemPath: String,
      pathCollector: PathCollector[F],
      pathPrefix: String,
      bufferSize: Int,
      cacheStrategy: CacheStrategy[F])

  object Config {
    def apply[F[_]: Async](
        systemPath: String,
        pathPrefix: String = "",
        bufferSize: Int = 50 * 1024,
        cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F]): Config[F] = {
      val pathCollector: PathCollector[F] = filesOnly
      Config(systemPath, pathCollector, pathPrefix, bufferSize, cacheStrategy)
    }
  }

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  private[staticcontent] def apply[F[_]](config: Config[F])(implicit F: Async[F]): HttpRoutes[F] = {
    object BadTraversal extends Exception with NoStackTrace
    Try(Paths.get(config.systemPath).toRealPath()) match {
      case Success(rootPath) =>
        TranslateUri(config.pathPrefix)(Kleisli { request =>
          def resolvedPath: OptionT[F, Path] = {
            val segments = request.pathInfo.segments.map(_.decoded(plusIsSpace = true))
            if (request.pathInfo.isEmpty) OptionT.some(rootPath)
            else
              OptionT
                .liftF(F.catchNonFatal {
                  segments.foldLeft(rootPath) {
                    case (_, "" | "." | "..") => throw BadTraversal
                    case (path, segment) =>
                      path.resolve(segment)
                  }
                })
          }
          resolvedPath
            .flatMapF(path =>
              F.delay(
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                  Some(path.toRealPath(LinkOption.NOFOLLOW_LINKS))
                else None))
            .collect { case path if path.startsWith(rootPath) => path.toFile }
            .flatMap(f => config.pathCollector(f, config, request))
            .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
            .recoverWith { case BadTraversal =>
              OptionT.some(Response(Status.BadRequest))
            }
        })

      case Failure(_: NoSuchFileException) =>
        logger.error(
          s"Could not find root path from FileService config: systemPath = ${config.systemPath}, pathPrefix = ${config.pathPrefix}. All requests will return none.")
        Kleisli(_ => OptionT.none)

      case Failure(e) =>
        logger.error(e)(
          s"Could not resolve root path from FileService config: systemPath = ${config.systemPath}, pathPrefix = ${config.pathPrefix}. All requests will fail with a 500.")
        Kleisli(_ => OptionT.pure(Response(Status.InternalServerError)))
    }
  }

  private def filesOnly[F[_]](file: File, config: Config[F], req: Request[F])(implicit
      F: Async[F]): OptionT[F, Response[F]] =
    OptionT(F.defer {
      if (file.isDirectory)
        StaticFile
          .fromFile(new File(file, "index.html"), Some(req))
          .value
      else if (!file.isFile) F.pure(None)
      else
        OptionT(getPartialContentFile(file, config, req))
          .orElse(
            StaticFile
              .fromFile(file, config.bufferSize, Some(req), StaticFile.calcETag)
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
  private def getPartialContentFile[F[_]](file: File, config: Config[F], req: Request[F])(implicit
      F: Async[F]): F[Option[Response[F]]] = {
    def nope: F[Option[Response[F]]] = F.delay(file.length()).map { size =>
      Some(
        Response[F](
          status = Status.RangeNotSatisfiable,
          headers = Headers
            .apply(AcceptRangeHeader, `Content-Range`(SubRange(0, size - 1), Some(size)))))
    }

    req.headers.get[Range] match {
      case Some(Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil))) =>
        if (validRange(s, e, file.length))
          F.defer {
            val size = file.length()
            val start = if (s >= 0) s else math.max(0, size + s)
            val end = math.min(size - 1, e.getOrElse(size - 1)) // end is inclusive

            StaticFile
              .fromFile(file, start, end + 1, config.bufferSize, Some(req), StaticFile.calcETag)
              .map { resp =>
                val hs = resp.headers
                  .put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
                resp.copy(status = Status.PartialContent, headers = hs)
              }
              .value
          }
        else nope
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
