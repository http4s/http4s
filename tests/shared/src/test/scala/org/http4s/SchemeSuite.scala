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

import cats.kernel.laws.discipline.OrderTests
import cats.syntax.all._
import org.http4s.Uri.Scheme
import org.http4s.internal.CharPredicate
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.all._
import org.http4s.util.Renderer
import org.scalacheck.Prop._

class SchemeSuite extends munit.DisciplineSuite {
  test("equals should be consistent with equalsIgnoreCase of the values") {
    forAll { (a: Scheme, b: Scheme) =>
      (a == b) == a.value.equalsIgnoreCase(b.value)
    }
  }

  test("compare should be consistent with value.compareToIgnoreCase") {
    forAll { (a: Scheme, b: Scheme) =>
      assertEquals(a.value.compareToIgnoreCase(b.value), a.compare(b))
    }
  }

  test("hashcode should be consistent with equality") {
    forAll { (a: Scheme, b: Scheme) =>
      (a == b) ==> (a.## == b.##)
    }
  }

  test("render should return value") {
    forAll { (s: Scheme) =>
      assertEquals(Renderer.renderString(s), s.value)
    }
  }

  test("fromString should reject all invalid schemes") { (s: String) =>
    (s.isEmpty ||
      !CharPredicate.Alpha(s.charAt(0)) ||
      !s.forall(CharPredicate.Alpha ++ CharPredicate(".-+"))) ==>
      Scheme.fromString(s).isLeft
  }

  test("fromString should accept valid literals prefixed by cached version") {
    assert(Scheme.fromString("httpx").isRight)
  }

  test("literal syntax should accept valid literals") {
    assertEquals(scheme"https", Scheme.https)
  }

  test("literal syntax should reject invalid literals") {
    compileErrors("""scheme"нет"""")
  }

  checkAll("Order[Scheme]", OrderTests[Scheme].order)
  checkAll("HttpCodec[Scheme]", HttpCodecTests[Scheme].httpCodec)
}
