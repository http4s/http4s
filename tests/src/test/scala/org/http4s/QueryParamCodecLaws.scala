/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats._
import cats.implicits._
import org.scalacheck.{Arbitrary, Properties}
import org.scalacheck.Prop._

/** Instances of [[QueryParamDecoder]] and [[QueryParamEncoder]]
  * must satisfy the following properties
  */
object QueryParamCodecLaws {
  val parseFailure = ParseFailure("For Test", "Let's assume we didn't like this value")

  def apply[T: Arbitrary: Eq: QueryParamDecoder: QueryParamEncoder] =
    new Properties("QueryParamCodec") {
      property("decode . encode == successNel") = forAll { (value: T) =>
        (QueryParamDecoder[T].decode _)
          .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      }

      property("decode . emap(Right) . encode == successNel") = forAll { (value: T) =>
        (QueryParamDecoder[T].emap((t: T) => t.asRight[ParseFailure]).decode _)
          .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      }

      property("decode . emap(Left) . encode == failedNel") = forAll { (value: T) =>
        (QueryParamDecoder[T].emap(_ => parseFailure.asLeft[T]).decode _)
          .compose(QueryParamEncoder[T].encode)(value) === parseFailure.invalidNel
      }

      property("decode . emapValidatedNel(ValidNel) . encode == successNel") = forAll {
        (value: T) =>
          (QueryParamDecoder[T].emapValidatedNel((t: T) => t.validNel[ParseFailure]).decode _)
            .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      }

      property("decode . emapValidatedNel(InvalidNel) . encode == failedNel") = forAll {
        (value: T) =>
          (QueryParamDecoder[T].emapValidatedNel(_ => parseFailure.invalidNel[T]).decode _)
            .compose(QueryParamEncoder[T].encode)(value) === parseFailure.invalidNel
      }
    }
}
