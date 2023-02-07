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

package org.http4s.server.middleware.authentication

import cats.effect.Async
import cats.effect.Ref
import cats.effect.std.SecureRandom
import cats.effect.std.Semaphore
import cats.syntax.all._

import java.util.LinkedHashMap
import java.{util => ju}
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

private[authentication] object NonceKeeperF {
  def apply[F[_]](
      staleTimeout: Duration,
      nonceCleanupInterval: Duration,
      bits: Int,
  )(implicit F: Async[F]): F[NonceKeeperF[F]] = for {
    // This semaphore controls who has access to `nonces` during stale nonce eviction. This must never be set above one.
    semaphore <- Semaphore[F](1)
    current <- F.monotonic
    lastCleanupMillis <- Ref[F].of(current)
    nonces = new LinkedHashMap[String, NonceF[F]]
    random <- SecureRandom.javaSecuritySecureRandom[F]
  } yield new NonceKeeperF(
    staleTimeout,
    nonceCleanupInterval,
    bits,
    semaphore,
    lastCleanupMillis,
    nonces,
    random,
  )
}

/** A thread-safe class used to manage a database of nonces.
  *
  * @param staleTimeout Amount of time (in milliseconds) after which a nonce
  *                     is considered stale (i.e. not used for authentication
  *                     purposes anymore).
  * @param bits The number of random bits a nonce should consist of.
  */
private[authentication] class NonceKeeperF[F[_]](
    staleTimeout: Duration,
    nonceCleanupInterval: Duration,
    bits: Int,
    semaphore: Semaphore[F],
    lastCleanupMillis: Ref[F, FiniteDuration],
    nonces: LinkedHashMap[String, NonceF[F]],
    random: SecureRandom[F],
)(implicit F: Async[F]) {
  require(bits > 0, "Please supply a positive integer for bits.")

  /** Removes nonces that are older than staleTimeout
    * Note: this _MUST_ be executed with the singleton permit from the semaphore
    */
  private def unsafeCheckStale(): F[Unit] =
    for {
      now <- F.monotonic
      lastCleanupTime <- lastCleanupMillis.get
      result <-
        if (now - lastCleanupTime > nonceCleanupInterval) {
          lastCleanupMillis
            .set(now)
            .flatMap { _ =>
              // Because we are using an LinkedHashMap, the keys will be returned in the order they were
              // inserted. Therefore, once we reach a non-stale value, the remaining values are also not stale.
              F.tailRecM[ju.Iterator[NonceF[F]], Unit](nonces.values().iterator()) {
                case it if it.hasNext && staleTimeout > now - it.next().created =>
                  F.delay(Left {
                    it.remove()
                    it
                  })
                case _ => F.delay(Either.unit)
              }
            }
        } else F.pure(())
    } yield result

  /** Get a fresh nonce in form of a {@link String}.
    * @return A fresh nonce.
    */
  def newNonce(): F[String] =
    semaphore.permit.surround {
      for {
        _ <- unsafeCheckStale()
        n <- NonceF.gen[F](random, bits).iterateUntil(n => nonces.get(n.data) == null)
      } yield {
        nonces.put(n.data, n)
        n.data
      }
    }

  /** Checks if the nonce {@link data} is known and the {@link nc} value is
    * correct. If this is so, the nc value associated with the nonce is increased
    * and the appropriate status is returned.
    * @param data The nonce.
    * @param nc The nonce counter.
    * @return A reply indicating the status of (data, nc).
    */
  def receiveNonce(data: String, nc: Int): F[NonceKeeper.Reply] =
    semaphore.permit.surround {
      for {
        _ <- unsafeCheckStale()
        res <- nonces.get(data) match {
          case null => F.pure(NonceKeeper.StaleReply)
          case n: NonceF[F] =>
            n.nc.modify { lastNc =>
              if (nc > lastNc) {
                (lastNc + 1, NonceKeeper.OKReply)
              } else
                (lastNc, NonceKeeper.BadNCReply)
            }
        }
      } yield res
    }
}
