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

import cats.effect.Clock
import cats.effect.Concurrent
import cats.effect.Timer
import cats.effect.concurrent.Ref
import cats.effect.concurrent.Semaphore
import cats.syntax.all._
import java.util.LinkedHashMap
import scala.annotation.tailrec
import scala.concurrent.duration.MILLISECONDS

private[authentication] object NonceKeeper {
  def apply[F[_]: Timer](
      staleTimeout: Long,
      nonceCleanupInterval: Long,
      bits: Int,
  )(implicit F: Concurrent[F]): F[NonceKeeper[F]] = for {
    semaphore <- Semaphore[F](1)
    currentTime <- Clock[F].realTime(MILLISECONDS)
    lastCleanup <- Ref[F].of(currentTime)
  } yield new NonceKeeper(staleTimeout, nonceCleanupInterval, bits, semaphore, lastCleanup)

  sealed abstract class Reply

  case object StaleReply extends Reply

  case object OKReply extends Reply

  case object BadNCReply extends Reply
}

/** A thread-safe class used to manage a database of nonces.
  *
  * @param staleTimeout Amount of time (in milliseconds) after which a nonce
  *                     is considered stale (i.e. not used for authentication
  *                     purposes anymore).
  * @param bits The number of random bits a nonce should consist of.
  */
private[authentication] class NonceKeeper[F[_]: Timer](
    staleTimeout: Long,
    nonceCleanupInterval: Long,
    bits: Int,
    semaphore: Semaphore[F],
    lastCleanup: Ref[F, Long],
)(implicit F: Concurrent[F]) {
  require(bits > 0, "Please supply a positive integer for bits.")
  private val nonces = new LinkedHashMap[String, Nonce]

  val clock = Clock[F]

  /** Removes nonces that are older than staleTimeout
    * Note: this _MUST_ be executed inside a block synchronized on `nonces`
    */
  private def checkStale(): F[Unit] =
    for {
      d <- clock.realTime(MILLISECONDS)
      lastCleanupTime <- lastCleanup.get
      result <-
        if (d - lastCleanupTime > nonceCleanupInterval) {
          lastCleanup
            .set(d)
            .map { _ =>
              // Because we are using an LinkedHashMap, the keys will be returned in the order they were
              // inserted. Therefor, once we reach a non-stale value, the remaining values are also not stale.
              val it = nonces.values().iterator()
              @tailrec
              def dropStale(): Unit =
                if (it.hasNext && staleTimeout > d - it.next().created.getTime) {
                  it.remove()
                  dropStale()
                }
              dropStale()
            }
        } else F.pure(())
    } yield result

  /** Get a fresh nonce in form of a {@link String}.
    * @return A fresh nonce.
    */
  def newNonce(): F[String] = {
    var n: Nonce = null
    semaphore.withPermit {
      for {
        _ <- checkStale()
      } yield {
        n = Nonce.gen(bits)
        while (nonces.get(n.data) != null) n = Nonce.gen(bits)
        nonces.put(n.data, n)
        n.data
      }
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
    semaphore.withPermit {
      for {
        _ <- checkStale()
      } yield nonces.get(data) match {
        case null => NonceKeeper.StaleReply
        case n: Nonce =>
          if (nc > n.nc) {
            n.nc = n.nc + 1
            NonceKeeper.OKReply
          } else
            NonceKeeper.BadNCReply
      }
    }
}
