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
import cats.effect.concurrent.Ref
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
  def javaSecuritySecureRandom[F[_]](blocker: Blocker)(implicit F: Sync[F], cs: ContextShift[F]) = {
    // This is a known, non-blocking, threadsafe algorithm
    def happyRandom = F.delay(SecureRandom.getInstance("NativePRNGNonBlocking"))

    def fallback = F.delay(new SecureRandom())

    def isThreadsafe(rnd: SecureRandom) =
      rnd.getProvider
        .getProperty("SecureRandom." + rnd.getAlgorithm + " ThreadSafe", "false")
        .toBoolean

    // If we can't sniff out a more optimal solution, we can always
    // fall back to a pool of blocking instances
    def fallbackPool =
      F.delay(Runtime.getRuntime.availableProcessors())
        .flatMap(pool(_)(F.delay(new SecureRandom()).map(javaUtilRandomBlocking(blocker))))

    javaMajorVersion.flatMap {
      case Some(major) if major > 8 =>
        happyRandom.redeemWith(
          _ =>
            fallback.flatMap {
              case rnd if isThreadsafe(rnd) =>
                // We avoided the mutex, but not the blocking.  Use a
                // shared instance from the blocking pool.
                F.pure(javaUtilRandomBlocking(blocker)(rnd))
              case _ =>
                // We can't prove the instance is threadsafe, so we need
                // to pessimistically fall back to a pool.  This should
                // be exceedingly uncommon.
                fallbackPool
            },
          // We are thread safe and non-blocking.  This is the
          // happy path, and happily, the common path.
          rnd => F.pure(javaUtilRandomNonBlocking(rnd)),
        )

      case Some(_) | None =>
        // We can't guarantee we're not stuck in a mutex.
        fallbackPool
    }
  }

  def pool[F[_]](n: Int)(random: F[Random[F]])(implicit F: Sync[F]): F[Random[F]] =
    for {
      ref <- Ref[F].of(0)
      array <- Vector.fill(n)(random).sequence
    } yield {
      val incrGet = ref.modify(i => (if (i < (n - 1)) i + 1 else 0, i))
      val selectRandom = F.map(incrGet)(array(_))
      new Random[F] {
        def nextBigInt(bits: Int): F[BigInt] =
          F.flatMap(selectRandom)(_.nextBigInt(bits))
      }
    }
}
