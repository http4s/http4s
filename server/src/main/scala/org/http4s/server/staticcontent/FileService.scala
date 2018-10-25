package org.http4s
package server
package staticcontent

import cats.data._
import cats.effect._
import java.io.File
import org.http4s.headers.Range.SubRange
import org.http4s.headers._
import scala.concurrent.ExecutionContext

object FileService {
  type PathCollector[F[_]] = (File, Config[F], Request[F]) => OptionT[F, Response[F]]

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param pathPrefix prefix of Uri from which content will be served
    * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param bufferSize buffer size to use for internal read buffers
    * @param blockingExecutionContext `ExecutionContext` to use for blocking I/O
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config[F[_]](
      systemPath: String,
      pathCollector: PathCollector[F],
      pathPrefix: String,
      bufferSize: Int,
      blockingExecutionContext: ExecutionContext,
      cacheStrategy: CacheStrategy[F])

  object Config {
    def apply[F[_]: Sync: ContextShift](
        systemPath: String,
        pathPrefix: String = "",
        bufferSize: Int = 50 * 1024,
        executionContext: ExecutionContext = ExecutionContext.global,
        cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F]): Config[F] = {
      val pathCollector: PathCollector[F] = filesOnly
      Config(systemPath, pathCollector, pathPrefix, bufferSize, executionContext, cacheStrategy)
    }
  }

  /** Make a new [[org.http4s.HttpRoutes]] that serves static files. */
  private[staticcontent] def apply[F[_]](config: Config[F])(implicit F: Effect[F]): HttpRoutes[F] =
    Kleisli {
      case request if request.pathInfo.startsWith(config.pathPrefix) =>
        getFile(s"${config.systemPath}/${getSubPath(request.pathInfo, config.pathPrefix)}")
          .flatMap(f => config.pathCollector(f, config, request))
          .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
      case _ => OptionT.none
    }

  private def filesOnly[F[_]](file: File, config: Config[F], req: Request[F])(
      implicit F: Sync[F],
      cs: ContextShift[F]): OptionT[F, Response[F]] =
    OptionT(F.suspend {
      if (file.isDirectory)
        StaticFile
          .fromFile(new File(file, "index.html"), config.blockingExecutionContext, Some(req))
          .value
      else if (!file.isFile) F.pure(None)
      else
        getPartialContentFile(file, config, req)
          .orElse(
            StaticFile
              .fromFile(
                file,
                config.bufferSize,
                config.blockingExecutionContext,
                Some(req),
                StaticFile.calcETag)
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
  private def getPartialContentFile[F[_]](file: File, config: Config[F], req: Request[F])(
      implicit F: Sync[F],
      cs: ContextShift[F]): OptionT[F, Response[F]] =
    OptionT.fromOption[F](req.headers.get(Range)).flatMap {
      case Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil))
          if validRange(s, e, file.length) =>
        OptionT(F.suspend {
          val size = file.length()
          val start = if (s >= 0) s else math.max(0, size + s)
          val end = math.min(size - 1, e.getOrElse(size - 1)) // end is inclusive

          StaticFile
            .fromFile(
              file,
              start,
              end + 1,
              config.bufferSize,
              config.blockingExecutionContext,
              Some(req),
              StaticFile.calcETag)
            .map { resp =>
              val hs: Headers = resp.headers
                .put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
              resp.copy(status = Status.PartialContent, headers = hs)
            }
            .value
        })

      case _ => OptionT.none
    }

  // Attempts to sanitize the file location and retrieve the file. Returns None if the file doesn't exist.
  private def getFile[F[_]](unsafePath: String)(implicit F: Sync[F]): OptionT[F, File] =
    OptionT(F.delay {
      val f = new File(Uri.removeDotSegments(unsafePath))
      if (f.exists()) Some(f)
      else None
    })
}
