package org.http4s.server.staticcontent

import org.http4s._

import scodec.bits.ByteVector

import java.util.HashMap

import scalaz.concurrent.Task
import scalaz.stream.Process

import org.log4s.getLogger


trait CacheStrategy {
  def cache(path: Uri, resp: Response): Task[Response]
}

object NoopCacheStrategy extends CacheStrategy {
  override def cache(path: Uri, resp: Response): Task[Response] = Task.now(resp)
}

object MemoryCache {
  def apply(): MemoryCache = new MemoryCache
}

class MemoryCache extends CacheStrategy {
  private val logger = getLogger
  private val cacheMap = new HashMap[String, Response]()

  override def cache(path: Uri, resp: Response): Task[Response] = {
    if (resp.status == Status.Ok) {
      val pathStr = path.path
      Option(cacheMap.synchronized(cacheMap.get(pathStr))) match {
        case Some(r) if r.headers.toList == resp.headers.toList =>
          logger.debug(s"Cache hit: $resp")
          Task.now(r)

        case _ =>
          logger.debug(s"Cache miss: $resp")
          collectResource(pathStr, resp) /* otherwise cache the response */
      }
    }
    else Task.now(resp)
  }

  ////////////// private methods //////////////////////////////////////////////

  private def collectResource(path: String, resp: Response): Task[Response] = {
    resp.body
      .runLog
      .map { bytes =>
        val bv = ByteVector.view(bytes.foldLeft(ByteVector.empty)(_ ++ _).toArray)
        val newResponse = resp.copy(body = Process.emit(bv))
        cacheMap.synchronized { cacheMap.put(path, newResponse) }
        newResponse
      }
  }
}
