/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.digest

import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, Timer}
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class SimpleInMemoryStoreNonceCounter[F[_]](
    nonceTtl: FiniteDuration,
    cnonceF: () => String,
    maxNonce: Long = NonceCounter.MaxNonce)(implicit T: Timer[F], C: Concurrent[F])
    extends NonceCounter[F] {
  private val cacheRef: Ref[F, Map[String, Entry]] = Ref.unsafe(Map.empty)
  override def next(nonce: String): F[(String, Long)] =
    (for {
      now <- T.clock.monotonic(TimeUnit.NANOSECONDS)
      res <- cacheRef.updateAndGet { nonceMap =>
        val current = nonceMap.getOrElse(nonce, Entry(now, cnonceF(), 0L))
        val nextCandidate = current.copy(usedAt = now, nc = current.nc + 1L)
        val next = if (nextCandidate.nc > maxNonce) Entry(now, cnonceF(), 1L) else nextCandidate
        nonceMap + (nonce -> next)
      }
    } yield (res(nonce).cnonce, res(nonce).nc)) <* C.start(autoCleanIfNotUsed(nonce))

  private def autoCleanIfNotUsed(nonce: String): F[Unit] =
    for {
      time <- T.clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- T.sleep(nonceTtl)
      _ <- cacheRef.update { nonceMap =>
        nonceMap.get(nonce) match {
          case Some(r) => if (r.usedAt > time) nonceMap else nonceMap - nonce
          case None => nonceMap
        }
      }
    } yield ()

  override def evict(nonce: String): F[Unit] =
    cacheRef.update(_ - nonce)

  override def evictAll: F[Unit] =
    cacheRef.set(Map.empty[String, Entry])

  case class Entry(usedAt: Long, cnonce: String, nc: Long)
}
