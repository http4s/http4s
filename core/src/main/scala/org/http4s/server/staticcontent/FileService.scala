/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import java.io.File
import java.nio.file.NoSuchFileException
import java.nio.file.{LinkOption, Path, Paths}
import org.http4s.headers.Range.SubRange
import org.http4s.headers._
import org.http4s.server.middleware.TranslateUri
import org.log4s.getLogger
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
      blocker: Blocker,
      pathCollector: PathCollector[F],
      pathPrefix: String,
      bufferSize: Int,
      cacheStrategy: CacheStrategy[F])

  object Config {
    def apply[F[_]: Sync: ContextShift](
        systemPath: String,
        blocker: Blocker,
        pathPrefix: String = "",
        bufferSize: Int = 50 * 1024,
        cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F]): Config[F] = {
      val pathCollector: PathCollector[F] = filesOnly
      Config(systemPath, blocker, pathCollector, pathPrefix, bufferSize, cacheStrategy)
    }
  }

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  private[staticcontent] def apply[F[_]](config: Config[F])(implicit F: Sync[F]): HttpRoutes[F] = {
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
            .semiflatMap(path => F.delay(path.toRealPath(LinkOption.NOFOLLOW_LINKS)))
            .collect { case path if path.startsWith(rootPath) => path.toFile }
            .flatMap(f => config.pathCollector(f, config, request))
            .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
            .recoverWith {
              case _: NoSuchFileException => OptionT.none
              case BadTraversal => OptionT.some(Response(Status.BadRequest))
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
      F: Sync[F],
      cs: ContextShift[F]): OptionT[F, Response[F]] =
    OptionT(F.suspend {
      if (file.isDirectory)
        StaticFile
          .fromFile(new File(file, "index.html"), config.blocker, Some(req))
          .value
      else if (!file.isFile) F.pure(None)
      else
        OptionT(getPartialContentFile(file, config, req))
          .orElse(
            StaticFile
              .fromFile(file, config.bufferSize, config.blocker, Some(req), StaticFile.calcETag)
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
      F: Sync[F],
      cs: ContextShift[F]): F[Option[Response[F]]] =
    req.headers.get(Range) match {
      case Some(Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil))) =>
        if (validRange(s, e, file.length))
          F.suspend {
            val size = file.length()
            val start = if (s >= 0) s else math.max(0, size + s)
            val end = math.min(size - 1, e.getOrElse(size - 1)) // end is inclusive

            StaticFile
              .fromFile(
                file,
                start,
                end + 1,
                config.bufferSize,
                config.blocker,
                Some(req),
                StaticFile.calcETag)
              .map { resp =>
                val hs: Headers = resp.headers
                  .put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
                resp.copy(status = Status.PartialContent, headers = hs)
              }
              .value
          }
        else
          F.delay(file.length()).map { size =>
            Some(
              Response[F](
                status = Status.RangeNotSatisfiable,
                headers = Headers
                  .of(AcceptRangeHeader, `Content-Range`(SubRange(0, size - 1), Some(size)))))
          }
      case _ => F.pure(None)
    }
}
