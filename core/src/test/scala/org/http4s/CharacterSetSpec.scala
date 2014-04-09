package org.http4s

import org.scalatest.{WordSpec, Matchers}
import org.http4s.util.string._
import java.nio.charset.UnsupportedCharsetException

class CharacterSetSpec extends WordSpec with Matchers {
  import CharacterSet._

  def resolve(str: String) = CharacterSet.getOrElseCreate(str.ci)

  "CharacterSet.apply" should {
    "check the registry" in {
      resolve("UTF-8") should be theSameInstanceAs `UTF-8`
    }

    "be case-insensitive" in {
      resolve("utf-8") should be theSameInstanceAs `UTF-8`
    }

    "check common aliases" in {
      resolve("UTF8") should be theSameInstanceAs `UTF-8`
    }

    "create new charsets if supported by the JVM" in {
      resolve("ISO-8859-2").name should equal ("ISO-8859-2".ci)
    }

    "fail for unknown charsets" in {
      val cs = resolve("derp")
      an [UnsupportedCharsetException] should be thrownBy cs.charset
    }
  }

  "A CharacterSet" should {
    "match itself" in {
      `UTF-8` satisfiedBy `UTF-8` should be (true)
    }

    "not match others" in {
      `UTF-8` satisfiedBy `ISO-8859-1` should be (false)
    }

    "Be orderable" in {
      val expected = List(`UTF-16BE`, `UTF-16`.withQuality(0.2), `US-ASCII`.withQuality(0.1))
      val unordered = List(`UTF-16`.withQuality(0.2), `US-ASCII`.withQuality(0.1), `UTF-16BE`)

      unordered.sorted should equal(expected)
    }
  }

  "*" should {
    "be satisfied by UTF-8" in {
      `*` satisfiedBy `UTF-8` should be (true)
      `UTF-8` satisfies `*` should be (true)
    }

    "not be satisfied if q=0.0" in {
      `*` withQuality 0.0 satisfiedBy `UTF-8` should be (false)
      `UTF-8` satisfies(`*` withQuality 0.0) should be (false)
    }
  }

  "Not accept illegal q values" in {
    an [IllegalArgumentException] should be thrownBy `*`.withQuality(2.0)
    an [IllegalArgumentException] should be thrownBy `*`.withQuality(-2.0)

    an [IllegalArgumentException] should be thrownBy `UTF-8`.withQuality(2.0)
    an [IllegalArgumentException] should be thrownBy `UTF-8`.withQuality(-2.0)
  }
}
