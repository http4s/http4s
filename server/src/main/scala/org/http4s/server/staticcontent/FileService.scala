package org.http4s
package server
package staticcontent

import java.io.File

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import org.http4s.headers.Range.SubRange
import org.http4s.headers._

import scala.concurrent.ExecutionContext

object FileService {
  type PathCollector[F[_]] = (File, Config[F], Request[F]) => F[Option[Response[F]]]

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param pathPrefix prefix of Uri from which content will be served
    * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param bufferSize buffer size to use for internal read buffers
    * @param executionContext `ExecutionContext` to use when collecting content
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](systemPath: String,
                                pathCollector: PathCollector[F],
                                pathPrefix: String,
                                bufferSize: Int,
                                executionContext: ExecutionContext,
                                cacheStrategy: CacheStrategy[F])

  object Config {
    def apply[F[_]: Sync](systemPath: String,
                          pathPrefix: String = "",
                          bufferSize: Int = 50 * 1024,
                          executionContext: ExecutionContext= ExecutionContext.global,
                          cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F]): Config[F] = {
      val pathCollector: PathCollector[F] = filesOnly
      Config(systemPath, pathCollector, pathPrefix, bufferSize, executionContext, cacheStrategy)
    }
  }

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply[F[_]](config: Config[F])(implicit F: Sync[F]): HttpService[F] = Service.lift {
    req =>
      val uriPath = req.pathInfo
      if (!uriPath.startsWith(config.pathPrefix))
        Pass.pure
      else
        getFile(config.systemPath + '/' + getSubPath(uriPath, config.pathPrefix))
          .map(f => config.pathCollector(f, config, req))
          .getOrElse(F.pure(None))
          .flatMap(_.fold(Pass.pure[F])(config.cacheStrategy.cache(uriPath, _).widen[MaybeResponse[F]]))
  }

  /* Returns responses for static files.
   * Directories are forbidden.
   */
  private def filesOnly[F[_]](file: File, config: Config[F], req: Request[F])(implicit F: Sync[F]): F[Option[Response[F]]] =
    F.pure {
      if (file.isDirectory) Some(Response(Status.Unauthorized))
      else if (!file.isFile) None
      else
        getPartialContentFile(file, config, req) orElse
          StaticFile
            .fromFile(file, config.bufferSize, Some(req))
            .map(_.putHeaders(AcceptRangeHeader))
    }

  private def validRange(start: Long, end: Option[Long], fileLength: Long): Boolean =
    start < fileLength && (end match {
      case Some(end) => start >= 0 && start <= end
      case None      => start >= 0 || fileLength + start - 1 >= 0
    })

  // Attempt to find a Range header and collect only the subrange of content requested
  private def getPartialContentFile[F[_]: Sync](file: File, config: Config[F], req: Request[F]): Option[Response[F]] =
    req.headers.get(Range).flatMap {
      case Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil)) if validRange(s, e, file.length) =>
        val size  = file.length()
        val start = if (s >= 0) s else math.max(0, size + s)
        val end   = math.min(size - 1, e getOrElse (size - 1)) // end is inclusive

        StaticFile
          .fromFile(file, start, end + 1, config.bufferSize, Some(req))
          .map { resp =>
            val hs: Headers = resp.headers.put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
            resp.copy(status = Status.PartialContent, headers = hs)
          }

      case _ => None
    }

  // Attempts to sanitize the file location and retrieve the file. Returns None if the file doesn't exist.
  private def getFile(unsafePath: String): Option[File] = {
    val f = new File(sanitize(unsafePath))
    if (f.exists()) Some(f)
    else None
  }
}
