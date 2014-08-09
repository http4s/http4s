package org.http4s

import java.nio.charset.{Charset => NioCharset}
import java.util.Locale
import scalaz.syntax.id._

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
      Charset.fromString("UTF8") must_== Charset.`UTF-8`.right
    }

    "return InvalidCharset for unregistered names" in {
      Charset.fromString("blah") must beLeftDisjunction
    }
  }
}
