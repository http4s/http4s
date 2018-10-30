package org.http4s

import java.nio.charset.{Charset => NioCharset}

class CharsetSpec extends Http4sSpec with PlatformCasing {
  "fromString" should {
    "be case insensitive" in {
      prop { cs: NioCharset =>
        val upper = toUpperCase(cs.name)
        val lower = toLowerCase(cs.name)
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
}
