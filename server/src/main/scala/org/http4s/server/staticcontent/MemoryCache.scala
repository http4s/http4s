package org.http4s
package server
package staticcontent

import java.util.concurrent.ConcurrentHashMap

import fs2._
import fs2.interop.cats._
import fs2.Stream._
import org.http4s.util.chunk._
import org.log4s.getLogger

/** [[CacheStrategy]] that will cache __all__ [[Response]] bodies in local memory
  *
  * This is useful when serving a very limited amount of static content and want
  * to avoid disk access.
  */
class MemoryCache extends CacheStrategy {
  private val logger = getLogger
  private val cacheMap = new ConcurrentHashMap[String, Response]()

  override def cache(uriPath: String, resp: Response): Task[Response] = {
    if (resp.status == Status.Ok) {
      Option(cacheMap.get(uriPath)) match {
        case Some(r) if r.headers.toList == resp.headers.toList =>
          logger.debug(s"Cache hit: $resp")
          Task.now(r)

        case _ =>
          logger.debug(s"Cache miss: $resp")
          collectResource(uriPath, resp) /* otherwise cache the response */
      }
    }
    else Task.now(resp)
  }

  ////////////// private methods //////////////////////////////////////////////

  private def collectResource(path: String, resp: Response): Task[Response] = {
    resp.body.chunks.runFoldMap[Chunk[Byte]](identity)
      .map { bytes =>
        val newResponse = resp.copy(body = chunk(bytes))
        cacheMap.put(path, newResponse)
        newResponse
      }
  }
}

object MemoryCache {
  def apply(): MemoryCache = new MemoryCache
}
