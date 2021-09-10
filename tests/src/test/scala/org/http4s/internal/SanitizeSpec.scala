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
package internal

import org.http4s.internal.parboiled2.CharPredicate
import org.scalacheck.Prop._

class SanitizeSpec extends Http4sSuite {
  test("eliminates invalid characters") {
    forAll { (s: String, invalid: Set[Char], replacement: Char) =>
      !invalid.contains(replacement) ==> {
        val cp = CharPredicate(invalid.toSeq)
        val sanitized = sanitize(s, cp, replacement)
        assert(sanitized.forall(cp.negated), sanitized)
      }
    }
  }

  test("returns string of same length") {
    forAll { (s: String, invalid: Set[Char], replacement: Char) =>
      val cp = CharPredicate(invalid.toSeq)
      assertEquals(sanitize(s, cp, replacement).length, s.length)
    }
  }

  test("returns same string if all valid") {
    forAll { (s: String, invalid: Set[Char], replacement: Char) =>
      val cp = CharPredicate(invalid.toSeq)
      s.forall(cp.negated) ==> {
        assertEquals(sanitize(s, cp, replacement), s)
      }
    }
  }
}
