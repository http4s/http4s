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

import cats.effect.kernel.Async
import cats.effect.std.Random
import cats.syntax.all._

import scala.concurrent.duration._

trait NonceCounter[F[_]] {

  /** Returns `cnonce` and next `nc` value for the given nonce */
  def next(nonce: String): F[(String, Long)]

  /** Call when a conversation is over, to prevent unbounded growth. */
  def evict(nonce: String): F[Unit]

  /** Call when a conversation is over, to prevent unbounded growth. */
  def evictAll: F[Unit]
}

object NonceCounter {
  val MaxNonce: Long = java.lang.Long.parseLong("FFFFFFFF", 16)

  def make[F[_]: Async](
      ttl: FiniteDuration = 1.minute,
      maxNonce: Long = MaxNonce,
  ): F[NonceCounter[F]] =
    Random
      .javaSecuritySecureRandom[F]
      .map(rand => new SimpleInMemoryStoreNonceCounter[F](ttl, rand, maxNonce))

}
