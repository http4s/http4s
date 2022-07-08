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

package org.http4s.client.digest

import cats.effect.Async
import cats.effect.Ref
import cats.effect.std.Random
import cats.syntax.all._

import java.util.Base64
import scala.concurrent.duration.FiniteDuration

class SimpleInMemoryStoreNonceCounter[F[_]](
    nonceTtl: FiniteDuration,
    seed: Random[F],
    maxNonce: Long,
)(implicit F: Async[F])
    extends NonceCounter[F] {

  private def nextCnonce(random: Random[F]): F[String] =
    random.nextBytes(16).map(Base64.getEncoder.encodeToString)

  private val cacheRef: Ref[F, Map[String, Entry]] = Ref.unsafe(Map.empty)

  override def next(nonce: String): F[(String, Long)] =
    for {
      now <- F.monotonic
      cnonce <- nextCnonce(seed)
      map <- cacheRef.updateAndGet { nonceMap =>
        val current = nonceMap.getOrElse(nonce, Entry(now.toNanos, cnonce, 0L))
        val nextCandidate = current.copy(usedAt = now.toNanos, nc = current.nc + 1L)
        val next =
          if (nextCandidate.nc > maxNonce)
            Entry(now.toNanos, cnonce, 1L)
          else
            nextCandidate
        nonceMap + (nonce -> next)
      }
      _ <- F.start(autoCleanIfNotUsed(nonce))
    } yield map(nonce).cnonce -> map(nonce).nc

  private def autoCleanIfNotUsed(nonce: String): F[Unit] =
    for {
      time <- F.monotonic
      _ <- F.sleep(nonceTtl)
      _ <- cacheRef.update { nonceMap =>
        nonceMap.get(nonce) match {
          case Some(r) => if (r.usedAt > time.toNanos) nonceMap else nonceMap - nonce
          case None => nonceMap
        }
      }
    } yield ()

  override def evict(nonce: String): F[Unit] =
    cacheRef.update(_ - nonce)

  override def evictAll: F[Unit] =
    cacheRef.set(Map.empty[String, Entry])

  private case class Entry(usedAt: Long, cnonce: String, nc: Long)
}
