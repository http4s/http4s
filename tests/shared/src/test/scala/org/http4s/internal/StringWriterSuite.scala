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
package util

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.Prop._

class StringWriterSuite extends Http4sSuite {

  test("sanitize works on chars") {
    val sw = new StringWriter
    sw.sanitize(_ << 'x' << 0x0.toChar << '\r' << '\n' << 'x')
    assertEquals("x   x", sw.result)
  }

  test("sanitize works on strings") {
    val sw = new StringWriter
    sw.sanitize(_ << "x\u0000\r\nx")
    assertEquals("x   x", sw.result)
  }

  test("sanitizes between appends") {
    val forbiddenChars = Set(0x0.toChar, '\r', '\n')
    val unsanitaryGen = stringOf(oneOf(oneOf(forbiddenChars), arbitrary[Char]))
    forAll(arbitrary[String], unsanitaryGen, arbitrary[String]) {
      (s1: String, s2: String, s3: String) =>
        s2.exists(forbiddenChars) ==> {
          val sw = new StringWriter
          (sw << s1).sanitize(_ << s2) << s3
          val s = sw.result
          assert(s.startsWith(s1))
          assert(s.endsWith(s3))
          assert(!s.drop(s1.length).dropRight(s3.length).exists(forbiddenChars))
        }
    }
  }
}
