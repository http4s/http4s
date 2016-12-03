package org.http4s
package server
package staticcontent

import java.util.concurrent.ExecutorService

import fs2._

object ResourceService {

  /** [[org.http4s.server.staticcontent.ResourceService]] configuration
    *
    * @param basePath prefix of the path files will be served from
    * @param pathPrefix prefix of the Uri that content will be served from
    * @param bufferSize size hint of internal buffers to use when serving resources
    * @param executor `ExecutorService` to use when collecting content
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config(basePath: String,
                          pathPrefix: String = "",
                          bufferSize: Int = 50*1024,
                          executor: ExecutorService,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy)

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): Service[Request, Response] = Service.lift { req =>
    implicit val executor = config.executor
    val uriPath = req.pathInfo
    if (!uriPath.startsWith(config.pathPrefix))
      Response.fallthrough
    else
      StaticFile.fromResource(sanitize(config.basePath + '/' + getSubPath(uriPath, config.pathPrefix)))
        .fold(Response.fallthrough)(config.cacheStrategy.cache(uriPath, _))
  }
}
