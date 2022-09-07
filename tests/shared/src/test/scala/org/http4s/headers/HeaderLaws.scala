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

import org.http4s.syntax.header._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import org.typelevel.discipline.Laws

trait HeaderLaws extends munit.DisciplineSuite with Laws {
  def headerLaws[A](implicit
      arbHeader: Arbitrary[A],
      header: Header[A, _],
      select: Header.Select[A],
  ): RuleSet =
    new SimpleRuleSet(
      "header",
      """parse(a.value) == right(a)"""" -> forAll { (a: A) =>
        assertEquals(header.parse(a.value), Right(a))
      },
      """renderString == "name: value"""" -> forAll { (a: A) =>
        assertEquals(a.renderString, s"${a.name}: ${a.value}")
      },
      """header matches itself""" -> forAll { (a: A) =>
        assertEquals(Headers(a.toRaw1).get[A].get.asInstanceOf[A], a)
      },
      """header does not match arbitrary name""" -> forAll { (a: A, noise: String) =>
        noise.nonEmpty ==> {
          val malformedName = a.name.toString + noise
          val properValue = a.value
          unitToProp(assert(Headers((malformedName, properValue)).get[A].isEmpty))
        }
      },
    )
}
