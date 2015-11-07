package org.http4s

import java.nio.charset.{Charset => NioCharset}
import java.util.Locale

class CharsetSpec extends Http4sSpec {
  "fromString" should {
    "be case insensitive" in {
      prop { cs: NioCharset =>
        val upper = cs.name.toUpperCase(Locale.ROOT)
        val lower = cs.name.toLowerCase(Locale.ROOT)
        Charset.fromString(upper) must_== Charset.fromString(lower)
      }
    }

    "work for aliases" in {
      Charset.fromString("UTF8") must be_\/-(Charset.`UTF-8`)
    }

    "return InvalidCharset for unregistered names" in {
      Charset.fromString("blah") must be_-\/
    }
  }
}
