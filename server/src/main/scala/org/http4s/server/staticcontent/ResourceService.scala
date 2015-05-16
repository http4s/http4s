package org.http4s.server.staticcontent

import java.io.File
import java.util.concurrent.ExecutorService

import org.http4s.server._
import org.http4s.StaticFile

import scalaz.concurrent.{Strategy, Task}
import scalaz.OptionT


object ResourceService {

  case class Config(basePath: String,
                    pathPrefix: String = "",
                    bufferSize: Int = 50*1024,
                    executor: ExecutorService = Strategy.DefaultExecutorService,
                    cacheStartegy: CacheStrategy = NoopCacheStrategy)

  /** Make a new [[org.http4s.server.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): HttpService = Service.lift { req =>
    val uri = req.uri
    if (!uri.path.startsWith(config.pathPrefix)) Task.now(None)
    else OptionT(
          StaticFile.fromResource(sanitize(config.basePath + '/' + getSubPath(uri, config.pathPrefix)))
          .map{ f => Task.now(Some(f)) }
          .getOrElse(Task.now(None))
        )
          .flatMapF(config.cacheStartegy.cache(uri, _))
          .run
  }
}
