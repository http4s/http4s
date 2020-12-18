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

import cats.syntax.all._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.Uri.Scheme
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer

class SchemeSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the values" in {
      prop { (a: Scheme, b: Scheme) =>
        (a == b) must_== a.value.equalsIgnoreCase(b.value)
      }
    }
  }

  "compare" should {
    "be consistent with value.compareToIgnoreCase" in {
      prop { (a: Scheme, b: Scheme) =>
        a.value.compareToIgnoreCase(b.value) must_== a.compare(b)
      }
    }
  }

  "hashCode" should {
    "be consistent with equality" in {
      prop { (a: Scheme, b: Scheme) =>
        (a == b) ==> (a.## must_== b.##)
      }
    }
  }

  "render" should {
    "return value" in prop { (s: Scheme) =>
      Renderer.renderString(s) must_== s.value
    }
  }

  "fromString" should {
    "reject all invalid schemes" in { (s: String) =>
      (s.isEmpty ||
        !CharPredicate.Alpha(s.charAt(0)) ||
        !s.forall(CharPredicate.Alpha ++ CharPredicate(".-+"))) ==>
        (Scheme.fromString(s) must beLeft)
    }

    "accept valid literals prefixed by cached version" in {
      Scheme.fromString("httpx") must beRight
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      scheme"https" must_== Scheme.https
    }

    "reject invalid literals" in {
      illTyped("""scheme"нет"""")
      true
    }
  }

  checkAll("Order[Scheme]", OrderTests[Scheme].order)
  checkAll("HttpCodec[Scheme]", HttpCodecTests[Scheme].httpCodec)
}
