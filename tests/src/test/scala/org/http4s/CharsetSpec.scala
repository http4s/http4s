/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
