package org.http4s
package server
package staticcontent

import cats.data.{Kleisli, OptionT}
import cats.effect._
import scala.concurrent.ExecutionContext

object ResourceService {

  /** [[org.http4s.server.staticcontent.ResourceService]] configuration
    *
    * @param basePath prefix of the path files will be served from
    * @param pathPrefix prefix of the Uri that content will be served from
    * @param bufferSize size hint of internal buffers to use when serving resources
    * @param executionContext `ExecutionContext` to use when collecting content
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    * @param preferGzipped whether to serve pre-gzipped files (with extension ".gz") if they exist
    */
  final case class Config[F[_]](
      basePath: String,
      pathPrefix: String = "",
      bufferSize: Int = 50 * 1024,
      executionContext: ExecutionContext = ExecutionContext.global,
      cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
      preferGzipped: Boolean = false)

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply[F[_]: Effect](config: Config[F]): HttpService[F] =
    Kleisli {
      case request if request.pathInfo.startsWith(config.pathPrefix) =>
        StaticFile
          .fromResource(
            PathNormalizer.removeDotSegments(
              s"${config.basePath}/${getSubPath(request.pathInfo, config.pathPrefix)}"),
            Some(request),
            preferGzipped = config.preferGzipped
          )
          .semiflatMap(config.cacheStrategy.cache(request.pathInfo, _))
      case _ => OptionT.none
    }
}
