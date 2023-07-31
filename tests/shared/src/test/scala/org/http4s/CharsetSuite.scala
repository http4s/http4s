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

import cats.kernel.laws.discipline._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop.forAll

import java.nio.charset.{Charset => NioCharset}
import java.util.Locale

class CharsetSuite extends Http4sSuite {

  test("fromString should be case insensitive") {
    forAll { (cs: NioCharset) =>
      val upper = cs.name.toUpperCase(Locale.ROOT)
      val lower = cs.name.toLowerCase(Locale.ROOT)
      assert(Charset.fromString(upper) == Charset.fromString(lower))
    }
  }

  test("fromString should work for aliases") {
    assert(Charset.fromString("UTF8") == Right(Charset.`UTF-8`))
  }

  test("fromString should return InvalidCharset for unregistered names") {
    assert(Charset.fromString("blah").isLeft)
  }

  checkAll("Order[Charset]", OrderTests[Charset].order)
  checkAll("Hash[Charset]", HashTests[Charset].hash)

}
