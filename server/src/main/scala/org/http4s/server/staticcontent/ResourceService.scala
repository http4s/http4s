package org.http4s
package server
package staticcontent

import java.util.concurrent.ExecutorService

import org.http4s.server._
import org.http4s.{Response, Request, StaticFile}
import org.http4s.util.threads.DefaultPool

import scalaz.concurrent.{Strategy, Task}


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
                          executor: ExecutorService = DefaultPool,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy)

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): HttpService= Service.lift { req =>
    val uriPath = req.pathInfo
    if (!uriPath.startsWith(config.pathPrefix))
      Pass.now
    else
      StaticFile.fromResource(PathNormalizer.removeDotSegments(config.basePath + '/' + getSubPath(uriPath, config.pathPrefix)), Some(req))
        .fold(Pass.now)(config.cacheStrategy.cache(uriPath, _))
  }
}
