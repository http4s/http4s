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

package org.http4s

import cats._
import cats.data._
import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.syntax.all._
import fs2.Chunk

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

package object internal {

  private[http4s] def unsafeToCompletionStage[F[_], A](
      fa: F[A],
      dispatcher: Dispatcher[F],
  )(implicit F: Sync[F]): CompletionStage[A] = {
    val cf = new CompletableFuture[A]()
    dispatcher.unsafeToFuture(fa.attemptTap {
      case Right(a) => F.delay { cf.complete(a); () }
      case Left(e) => F.delay { cf.completeExceptionally(e); () }
    })
    cf
  }

  private[http4s] def bug(message: String): AssertionError =
    new AssertionError(
      s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}"
    )

  // TODO Remove in 1.0. We can do better with MurmurHash3.
  private[http4s] def hashLower(s: String): Int = {
    var h = 0
    var i = 0
    val len = s.length
    while (i < len) {
      // Strings are equal ignoring case if either their uppercase or lowercase
      // forms are equal. Equality of one does not imply the other, so we need
      // to go in both directions. A character is not guaranteed to make this
      // round trip, but it doesn't matter as long as all equal characters
      // hash the same.
      h = h * 31 + Character.toLowerCase(Character.toUpperCase(s.charAt(i)))
      i += 1
    }
    h
  }

  private val utf8Bom: Chunk[Byte] = Chunk(0xef.toByte, 0xbb.toByte, 0xbf.toByte)

  private[http4s] def skipUtf8ByteOrderMark(chunk: Chunk[Byte]): Chunk[Byte] =
    if (chunk.size >= 3 && chunk.take(3) == utf8Bom)
      chunk.drop(3)
    else chunk

  // Helper functions for writing Order instances //

  /** This is the same as `Order.by(f).compare(a, b)`, but with the parameters
    * re-arraigned to make it easier to partially apply the function to two
    * instances of a type before supplying the `A => B`.
    *
    * The intended use case is that `f: A => B` will extract out a single
    * field from two instances of some Product type and then compare the value
    * of the field. This can then be done in turn for each field of a Product,
    * significantly reducing the amount of code needed to write an `Order`
    * instance for a Product with many fields.
    *
    * See the `Order` instance for `Uri` for an example of this usage.
    */
  private[http4s] def compareField[A, B: Order](
      a: A,
      b: A,
      f: A => B,
  ): Int =
    Order.by[A, B](f).compare(a, b)

  /** Given at least one `Int` intended to represent the result of a comparison
    * of two fields of some Product type, reduce the result to the first
    * non-zero value, or return 0 if all comparisons are 0.
    *
    * The intended use case for this function is to reduce the amount of code
    * needed to write an `Order` instance for Product types. One can use
    * [[compareField]] to generate a comparison for each field in a product
    * type, then apply this function to get a ordering for the entire Product
    * type.
    *
    * See the `Order` instance for `Uri` for an example of this usage.
    *
    * @note The values of the `NonEmptyChain` are encoded `F[Int]`, where `F`
    *       is some `Comonad`. The primary Comonads with which we are
    *       concerned are `Eval` and `Id`. `Eval` will give lazy evaluation of
    *       the Ordering, stopping as soon as the result is known, and `Id`
    *       will give strict evaluation, in the case where the caller has good
    *       reason to believe that evaluating the thunks will be slower than
    *       strictly evaluating the result over all fields.
    */
  private[http4s] def reduceComparisons_[F[_]: Comonad](
      comparisons: NonEmptyChain[F[Int]]
  ): Int = {
    val extractComparison: F[Int] => Option[Int] =
      _.extract match {
        case 0 => None
        case otherwise => Some(otherwise)
      }

    comparisons
      .reduceLeftTo(extractComparison) {
        case (None, next) => extractComparison(next)
        case (otherwise, _) => otherwise
      }
      .getOrElse(0)
  }

  /** Similar to [[reduceComparisons_]] but with the `F` type forced to `Eval`
    * for every comparison other than the first one. This encodes the commonly
    * desired use case of only evaluating the minimum number of comparisons
    * required to determine the ordering.
    */
  private[http4s] def reduceComparisons(
      head: Int,
      tail: Eval[Int]*
  ): Int =
    reduceComparisons_(NonEmptyChain(Eval.now(head), tail: _*))

  private[http4s] def appendSanitized(sb: StringBuilder, s: String): Unit = {
    val start = sb.length
    sb.append(s)
    for (i <- start until sb.length) {
      val c = sb.charAt(i)
      if (c == 0x0.toChar || c == '\r' || c == '\n') {
        sb.setCharAt(i, ' ')
      }
    }
  }
}
