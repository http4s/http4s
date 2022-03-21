/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Sync
import cats.syntax.all._

import java.math.{BigInteger => JBigInteger}
import java.security.SecureRandom
import java.util.{Random => JRandom}

/** A partial backport of Cats-Effect 3's `Random`. */
private[http4s] trait Random[F[_]] {

  /** Generates a random BigInt between 0 and (2^bits-1) inclusive. */
  def nextBigInt(bits: Int): F[BigInt]
}

private[http4s] object Random {

  /** Create a non-blocking Random instance.  This is more efficient
    * than [[javaSecurityRandom]], but requires a more thoughtful
    * instance of random
    *
    * @param random a non-blocking instance of java.util.Random.  It
    * is the caller's responsibility to assure that the instance is
    * non-blocking.  If the instance blocks during seeding, it is the
    * caller's responsibility to seed it.
    */
  def javaUtilRandomNonBlocking[F[_]](random: JRandom)(implicit F: Sync[F]): Random[F] =
    new Random[F] {
      def nextBigInt(bits: Int): F[BigInt] =
        F.delay(BigInt(new JBigInteger(bits, random)))
    }

  /** Creates a blocking Random instance.  All calls to nextBytes are
    * shifted to the blocking pool.  This is safer, but less
    * effecient, than [[javaUtilRandomNonBlocking]].
    *
    * @param random a potentially blocking instance of java.util.Random
    */
  def javaUtilRandomBlocking[F[_]](
      blocker: Blocker
  )(random: JRandom)(implicit F: Sync[F], cs: ContextShift[F]): Random[F] =
    new Random[F] {
      def nextBigInt(bits: Int): F[BigInt] =
        blocker.blockOn(F.delay(BigInt(new JBigInteger(bits, random))))
    }

  /** Creates a Random instance.  On most platforms, it will be
    * non-blocking.  If a non-blocking instance can't be guaranteed,
    * falls back to a blocking implementation.
    */
  def javaSecuritySecureRandom[F[_]](blocker: Blocker)(implicit F: Sync[F], cs: ContextShift[F]) =
    javaMajorVersion.flatMap {
      case Some(major) if major > 8 =>
        // nextBytes runs in a mutex until Java 9
        F.delay(SecureRandom.getInstance("NativePRNGNonBlocking"))
          .redeemWith(
            // We can't guarantee this doesn't block.
            _ => F.delay(new SecureRandom).map(javaUtilRandomBlocking[F](blocker)),
            // This algorithm blocks neither in seeding nor next bytes
            rnd => F.pure(javaUtilRandomNonBlocking[F](rnd)),
          )
      case Some(_) | None =>
        // We can't guarantee that nextBytes isn't stuck in a mutex.
        // We could do better on Java 8 by checking for a non-blocking
        // one and an instance pool, but, meh.
        F.delay(new SecureRandom).map(javaUtilRandomBlocking[F](blocker))
    }
}
