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
import java.nio.charset.{Charset => NioCharset}
import java.util.Locale

class CharsetSpec extends Http4sSpec {
  "fromString" should {
    "be case insensitive" in {
      prop { (cs: NioCharset) =>
        val upper = cs.name.toUpperCase(Locale.ROOT)
        val lower = cs.name.toLowerCase(Locale.ROOT)
        Charset.fromString(upper) must_== Charset.fromString(lower)
      }
    }

    "work for aliases" in {
      Charset.fromString("UTF8") must beRight(Charset.`UTF-8`)
    }

    "return InvalidCharset for unregistered names" in {
      Charset.fromString("blah") must beLeft
    }
  }

  "Order[Charset]" should {
    "be lawful" in {
      checkAll("Order[Charset]", OrderTests[Charset].order)
    }
  }

  "Hash[Charset]" should {
    "be lawful" in {
      checkAll("Hash[Charset]", HashTests[Charset].hash)
    }
  }
}
