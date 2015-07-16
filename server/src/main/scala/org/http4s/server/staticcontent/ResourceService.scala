package org.http4s.server.staticcontent

import java.util.concurrent.ExecutorService

import org.http4s.server._
import org.http4s.{Response, Request, StaticFile}

import scalaz.concurrent.{Strategy, Task}
import scalaz.OptionT


object ResourceService {

  /** [[ResourceService]] configuration
    *
    * @param basePath prefix of the path files will be served from
    * @param pathPrefix prefix of the Uri that content will be served from
    * @param bufferSize size hint of internal buffers to use when serving resources
    * @param executor [[ExecutorService]] to use when collecting content
    * @param cacheStartegy strategy to use for caching purposes. Default to no caching.
    */
  case class Config(basePath: String,
                    pathPrefix: String = "",
                    bufferSize: Int = 50*1024,
                    executor: ExecutorService = Strategy.DefaultExecutorService,
                    cacheStartegy: CacheStrategy = NoopCacheStrategy)

  /** Make a new [[org.http4s.server.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): PartialService[Request, Response] = PartialService.lift { req =>
    val uri = req.uri
    if (!uri.path.startsWith(config.pathPrefix))
      OptionT.none
    else
      OptionT(
        StaticFile.fromResource(sanitize(config.basePath + '/' + getSubPath(uri, config.pathPrefix)))
          .map{ f => Task.now(Some(f)) }
          .getOrElse(Task.now(None))
      ).flatMapF(config.cacheStartegy.cache(uri, _))
  }
}
