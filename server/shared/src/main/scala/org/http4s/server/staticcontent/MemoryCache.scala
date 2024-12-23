/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package staticcontent

import cats.effect.Concurrent
import cats.syntax.apply._
import cats.syntax.functor._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen
import scodec.bits.ByteVector

import java.util.concurrent.ConcurrentHashMap

/** [[CacheStrategy]] that will cache __all__ [[Response]] bodies in local memory
  *
  * This is useful when serving a very limited amount of static content and want
  * to avoid disk access.
  */
class MemoryCache[F[_]: LoggerFactoryGen] extends CacheStrategy[F] {
  private[this] val logger = LoggerFactory.getLogger[F]
  private val cacheMap = new ConcurrentHashMap[Uri.Path, Response[F]]()

  override def cache(uriPath: Uri.Path, resp: Response[F])(implicit
      F: Concurrent[F]
  ): F[Response[F]] =
    if (resp.status == Status.Ok)
      Option(cacheMap.get(uriPath)) match {
        case Some(r) if r.headers.headers == resp.headers.headers =>
          logger.debug(s"Cache hit: $resp").as(r)
        case _ =>
          logger.debug(s"Cache miss: $resp") *>
            collectResource(uriPath, resp) /* otherwise cache the response */
      }
    else F.pure(resp)

  // //////////// private methods //////////////////////////////////////////////

  private def collectResource(path: Uri.Path, resp: Response[F])(implicit
      F: Concurrent[F]
  ): F[Response[F]] =
    resp
      .as[ByteVector]
      .map { bv =>
        val newResponse: Response[F] = resp.copy(entity = Entity.Strict(bv))
        cacheMap.put(path, newResponse)
        newResponse
      }
}

object MemoryCache {
  def apply[F[_]: LoggerFactoryGen](): MemoryCache[F] = new MemoryCache[F]
}
