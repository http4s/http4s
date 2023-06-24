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
import cats.syntax.all._

package object internal {

  private[http4s] def bug(message: String): AssertionError =
    new AssertionError(
      s"This is a bug. Please report to https://github.com/http4s/http4s/issues: ${message}"
    )

  // Helper functions for writing Order instances //

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
