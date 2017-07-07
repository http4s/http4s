package org.http4s
package server
package staticcontent

import cats._
import cats.effect._
import cats.implicits._
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
    */
  final case class Config[F[_]](basePath: String,
                                pathPrefix: String = "",
                                bufferSize: Int = 50*1024,
                                executionContext: ExecutionContext = DefaultExecutionContext,
                                cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F])

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply[F[_]](config: Config[F])
                                        (implicit F: Monad[F], S: Sync[F]): HttpService[F] =
    Service.lift { req =>
      val uriPath = req.pathInfo
      if (!uriPath.startsWith(config.pathPrefix))
        Pass.pure[F]
      else
        StaticFile
          .fromResource(sanitize(config.basePath + '/' + getSubPath(uriPath, config.pathPrefix)), Some(req))
          .map(config.cacheStrategy.cache(uriPath, _).widen[MaybeResponse[F]])
          .getOrElse(Pass.pure[F])
    }
}
