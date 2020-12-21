/*
 * Copyright 2019 http4s.org
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
package laws
package discipline

import cats.Eq
import cats.effect._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop, Shrink}
import org.typelevel.discipline.Laws

trait EntityEncoderTests[F[_], A] extends Laws {
  def laws: EntityEncoderLaws[F, A]

  def entityEncoder(implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqFBoolean: Eq[F[Boolean]]
  ): RuleSet =
    new DefaultRuleSet(
      name = "EntityEncoder",
      parent = None,
      "accurateContentLength" -> Prop.forAll { (a: A) =>
        laws.accurateContentLengthIfDefined(a)
      },
      "noContentLengthInStaticHeaders" -> laws.noContentLengthInStaticHeaders,
      "noTransferEncodingInStaticHeaders" -> laws.noTransferEncodingInStaticHeaders
    )
}

object EntityEncoderTests {
  def apply[F[_], A](implicit
      effectF: Concurrent[F],
      entityEncoderFA: EntityEncoder[F, A]
  ): EntityEncoderTests[F, A] =
    new EntityEncoderTests[F, A] {
      val laws: EntityEncoderLaws[F, A] = EntityEncoderLaws.apply[F, A]
    }
}
