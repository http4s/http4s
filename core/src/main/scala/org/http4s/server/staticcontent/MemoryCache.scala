/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.effect.Sync
import cats.syntax.functor._
import fs2.{Chunk, Stream}
import java.util.concurrent.ConcurrentHashMap
import org.log4s.getLogger

/** [[CacheStrategy]] that will cache __all__ [[Response]] bodies in local memory
  *
  * This is useful when serving a very limited amount of static content and want
  * to avoid disk access.
  */
class MemoryCache[F[_]] extends CacheStrategy[F] {
  private[this] val logger = getLogger
  private val cacheMap = new ConcurrentHashMap[Uri.Path, Response[F]]()

  override def cache(uriPath: Uri.Path, resp: Response[F])(implicit F: Sync[F]): F[Response[F]] =
    if (resp.status == Status.Ok)
      Option(cacheMap.get(uriPath)) match {
        case Some(r) if r.headers.toList == resp.headers.toList =>
          logger.debug(s"Cache hit: $resp")
          F.pure(r)

        case _ =>
          logger.debug(s"Cache miss: $resp")
          collectResource(uriPath, resp) /* otherwise cache the response */
      }
    else F.pure(resp)

  ////////////// private methods //////////////////////////////////////////////

  private def collectResource(path: Uri.Path, resp: Response[F])(implicit
      F: Sync[F]): F[Response[F]] =
    resp
      .as[Chunk[Byte]]
      .map { chunk =>
        val newResponse: Response[F] = resp.copy(body = Stream.chunk(chunk).covary[F])
        cacheMap.put(path, newResponse)
        newResponse
      }
}

object MemoryCache {
  def apply[F[_]](): MemoryCache[F] = new MemoryCache[F]
}
