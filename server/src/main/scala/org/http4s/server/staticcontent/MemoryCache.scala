package org.http4s.server.staticcontent

import org.http4s._

import scodec.bits.ByteVector

import java.util.concurrent.ConcurrentHashMap

import scalaz.concurrent.Task
import scalaz.stream.Process

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
    resp.body
      .runLog
      .map { bytes =>
        // Collect the whole body to a primitive ByteVector view of a single Array[Byte]
        val bv = ByteVector.view(bytes.foldLeft(ByteVector.empty)(_ ++ _).toArray)
        val newResponse = resp.copy(body = Process.emit(bv))
        cacheMap.put(path, newResponse)
        newResponse
      }
  }
}

object MemoryCache {
  def apply(): MemoryCache = new MemoryCache
}