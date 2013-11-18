package org.http4s

import org.scalatest.{TryValues, WordSpec, Matchers}

class CharacterSetSpec extends WordSpec with Matchers {
  import CharacterSet._

  "CharacterSet.apply" should {
    "check the registry" in {
      CharacterSet.apply("UTF-8").get should be theSameInstanceAs `UTF-8`
    }

    "be case-insensitive" in {
      CharacterSet.apply("utf-8").get should be theSameInstanceAs `UTF-8`
    }

    "check common aliases" in {
      CharacterSet.apply("UTF8").get should be theSameInstanceAs `UTF-8`
    }

    "create new charsets if supported by the JVM" in {
      CharacterSet.apply("ISO-8859-2").get.name should equal ("ISO-8859-2")
    }

    "fail for unknown charsets" in {
      CharacterSet.apply("derp").isFailure should be (true)
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
