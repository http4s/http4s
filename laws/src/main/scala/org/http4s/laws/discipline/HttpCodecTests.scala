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
import cats.laws.discipline._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop
import org.scalacheck.Shrink
import org.typelevel.discipline.Laws

trait HttpCodecTests[A] extends Laws {
  def laws: HttpCodecLaws[A]

  def httpCodec(implicit arbitraryA: Arbitrary[A], shrinkA: Shrink[A], eqA: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "HTTP codec",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: A) =>
        laws.httpCodecRoundTrip(a)
      },
    )
}

object HttpCodecTests {
  def apply[A: HttpCodec]: HttpCodecTests[A] =
    new HttpCodecTests[A] {
      val laws: HttpCodecLaws[A] = HttpCodecLaws[A]
    }
}
