package org.http4s
package server
package staticcontent

import java.util.concurrent.ConcurrentHashMap

import cats._
import cats.implicits._
import fs2.Stream._
import fs2._
import org.http4s.syntax.streamCats._
import org.http4s.util.chunk._
import org.log4s.getLogger

/** [[CacheStrategy]] that will cache __all__ [[Response]] bodies in local memory
  *
  * This is useful when serving a very limited amount of static content and want
  * to avoid disk access.
  */
class MemoryCache[F[_]] extends CacheStrategy[F] {
  private[this] val logger = getLogger(classOf[MemoryCache[F]])
  private val cacheMap = new ConcurrentHashMap[String, Response[F]]()

  override def cache(uriPath: String, resp: Response[F])
                    (implicit F: MonadError[F, Throwable]): F[Response[F]] = {
    if (resp.status == Status.Ok) {
      Option(cacheMap.get(uriPath)) match {
        case Some(r) if r.headers.toList == resp.headers.toList =>
          logger.debug(s"Cache hit: $resp")
          F.pure(r)

        case _ =>
          logger.debug(s"Cache miss: $resp")
          collectResource(uriPath, resp) /* otherwise cache the response */
      }
    }
    else F.pure(resp)
  }

  ////////////// private methods //////////////////////////////////////////////

  private def collectResource(path: String, resp: Response[F])
                             (implicit F: MonadError[F, Throwable]): F[Response[F]] = {
    resp.body.chunks.runFoldMap[Chunk[Byte]](identity)
      .map { bytes =>
        val newResponse: Response[F] = resp.copy(body = chunk(bytes))
        cacheMap.put(path, newResponse)
        newResponse
      }
  }
}

object MemoryCache {
  def apply[F[_]](): MemoryCache[F] = new MemoryCache[F]
}
