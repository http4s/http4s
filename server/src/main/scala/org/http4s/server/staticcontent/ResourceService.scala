package org.http4s
package server
package staticcontent

import org.http4s.util.threads.DefaultExecutionContext

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
  final case class Config(basePath: String,
                          pathPrefix: String = "",
                          bufferSize: Int = 50*1024,
                          executionContext: ExecutionContext = DefaultExecutionContext,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy,
                          preferGzipped: Boolean = false)

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): HttpService = Service.lift { req =>
    val uriPath = req.pathInfo
    if (!uriPath.startsWith(config.pathPrefix))
      Pass.now
    else
      StaticFile
        .fromResource(
          sanitize(config.basePath + '/' + getSubPath(uriPath, config.pathPrefix)),
          req = Some(req),
          preferGzipped = config.preferGzipped
        )
        .fold(Pass.now)(config.cacheStrategy.cache(uriPath, _))
  }
}
