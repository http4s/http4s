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
package headers

import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import org.typelevel.discipline.Laws
import org.http4s.laws.discipline.ArbitraryInstances._

trait MHeaderLaws extends munit.DisciplineSuite with Laws {
  def headerLaws(key: HeaderKey)(implicit
      arbHeader: Arbitrary[key.HeaderT]
  ): RuleSet =
    new SimpleRuleSet(
      "header",
      """parse(a.value) == right(a)"""" -> forAll { (a: key.HeaderT) =>
        assertEquals(key.parse(a.value), Right(a))
      },
      """renderString == "name: value"""" -> forAll { (a: key.HeaderT) =>
        assertEquals(a.renderString, s"${key.name}: ${a.value}")
      },
      """matchHeader matches parsed values""" -> forAll { (a: key.HeaderT) =>
        assertEquals(key.matchHeader(a), Some(a))
      },
      """matchHeader matches raw, valid values of same name""" -> forAll { (a: key.HeaderT) =>
        assertEquals(key.matchHeader(a.toRaw), Some(a))
      },
      """matchHeader does not match other names""" -> forAll { (header: Header) =>
        key.name != header.name ==> assert(key.matchHeader(header).isEmpty)
      }
    )
}
