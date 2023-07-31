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
import cats.syntax.all._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import org.typelevel.discipline.Laws

/** Instances of [[QueryParamDecoder]] and [[QueryParamEncoder]]
  * must satisfy the following properties
  */
object QueryParamCodecLaws extends Laws {
  private val parseFailure = ParseFailure("For Test", "Let's assume we didn't like this value")

  def apply[T: Arbitrary: Eq: QueryParamDecoder: QueryParamEncoder] =
    new SimpleRuleSet(
      "QueryParamCodec",
      "decode . encode == successNel" -> forAll { (value: T) =>
        (QueryParamDecoder[T].decode _)
          .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      },
      "decode . emap(Right) . encode == successNel" -> forAll { (value: T) =>
        (QueryParamDecoder[T].emap((t: T) => t.asRight[ParseFailure]).decode _)
          .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      },
      "decode . emap(Left) . encode == failedNel" -> forAll { (value: T) =>
        (QueryParamDecoder[T].emap(_ => parseFailure.asLeft[T]).decode _)
          .compose(QueryParamEncoder[T].encode)(value) === parseFailure.invalidNel
      },
      "decode . emapValidatedNel(ValidNel) . encode == successNel" -> forAll { (value: T) =>
        (QueryParamDecoder[T].emapValidatedNel((t: T) => t.validNel[ParseFailure]).decode _)
          .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      },
      "decode . emapValidatedNel(InvalidNel) . encode == failedNel" -> forAll { (value: T) =>
        (QueryParamDecoder[T].emapValidatedNel(_ => parseFailure.invalidNel[T]).decode _)
          .compose(QueryParamEncoder[T].encode)(value) === parseFailure.invalidNel
      },
    )
}
