package org.http4s

import org.scalatest.{TryValues, WordSpec, Matchers}

class CharacterSetSpec extends WordSpec with Matchers {
  import CharacterSet._

  "CharacterSet.apply" should {
    "check the registry" in {
      CharacterSet.resolve("UTF-8") should be theSameInstanceAs `UTF-8`
    }

    "be case-insensitive" in {
      CharacterSet.resolve("utf-8") should be theSameInstanceAs `UTF-8`
    }

    "check common aliases" in {
      CharacterSet.resolve("UTF8") should be theSameInstanceAs `UTF-8`
    }

    "create new charsets if supported by the JVM" in {
      CharacterSet.resolve("ISO-8859-2").name should equal ("ISO-8859-2")
    }

    "fail for unknown charsets" in {
      an [IllegalArgumentException] should be thrownBy CharacterSet.resolve("derp")
    }
  }

  "A CharacterSet" should {
    "match itself" in {
      `UTF-8` matches `UTF-8` should be (true)
    }

    "not match others" in {
      `UTF-8` matches `ISO-8859-1` should be (false)
    }
  }

  "*" should {
    "match UTF-8" in {
      `*` matches `UTF-8` should be (true)
    }
  }
}
