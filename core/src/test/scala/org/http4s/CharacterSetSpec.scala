package org.http4s

import org.http4s.util.string._
import java.nio.charset.UnsupportedCharsetException

import org.specs2.mutable.Specification

class CharacterSetSpec extends Specification {
  import CharacterSet._

  def resolve(str: String) = CharacterSet.getOrElseCreate(str.ci)

  "CharacterSet.apply" should {
    "check the registry" in {
      resolve("UTF-8") must be (`UTF-8`)
    }

    "be case-insensitive" in {
      resolve("utf-8") must be (`UTF-8`)
    }

    "check common aliases" in {
      resolve("UTF8") must be (`UTF-8`)
    }

    "create new charsets if supported by the JVM" in {
      resolve("ISO-8859-2").name must_== ("ISO-8859-2".ci)
    }

    "fail for unknown charsets" in {
      val cs = resolve("derp")
      cs.charset must throwAn [UnsupportedCharsetException]
    }
  }

  "A CharacterSet" should {
    "match itself" in {
      `UTF-8` satisfiedBy `UTF-8` should beTrue
    }

    "not match others" in {
      `UTF-8` satisfiedBy `ISO-8859-1` should beFalse
    }

    "Be orderable" in {
      val expected = List(`UTF-16BE`, `UTF-16`.withQuality(0.2), `US-ASCII`.withQuality(0.1))
      val unordered = List(`UTF-16`.withQuality(0.2), `US-ASCII`.withQuality(0.1), `UTF-16BE`)

      unordered.sorted must_==(expected)
    }
  }

  "*" should {
    "be satisfied by UTF-8" in {
      `*` satisfiedBy `UTF-8` should beTrue
      `UTF-8` satisfies `*` should beTrue
    }

    "not be satisfied if q=0.0" in {
      `*` withQuality 0.0 satisfiedBy `UTF-8` should beFalse
      `UTF-8` satisfies(`*` withQuality 0.0) should beFalse
    }
  }

  "Not accept illegal q values" in {
    `*`.withQuality(2.0) must throwAn [IllegalArgumentException]
    `*`.withQuality(-2.0) must throwAn [IllegalArgumentException]

    `UTF-8`.withQuality(2.0) must throwAn [IllegalArgumentException]
    `UTF-8`.withQuality(-2.0) must throwAn [IllegalArgumentException]
  }
}
