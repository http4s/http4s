package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.testing.HttpCodecTests
import org.http4s.util.Renderer

class ContentCodingSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the codings and quality" in prop {
      (a: ContentCoding, b: ContentCoding) =>
        (a == b) must_== a.coding.equalsIgnoreCase(b.coding) && a.qValue == b.qValue
    }
  }

  "compare" should {
    "be consistent with coding.compareToIgnoreCase for same quality" in {
      prop { (a: ContentCoding, b: ContentCoding) =>
        (a.qValue == b.qValue) ==> (a.coding.compareToIgnoreCase(b.coding) must_== a.compare(b))
      }
    }
    "be consistent with qValue.compareTo for same coding" in {
      prop { (a: ContentCoding, b: ContentCoding) =>
        (a.coding.toLowerCase == b.coding.toLowerCase) must_== (a.qValue.compareTo(b.qValue) == a
          .compare(b))
      }
    }
  }

  "hashCode" should {
    "be consistent with equality" in
      prop { (a: ContentCoding, b: ContentCoding) =>
        a == b must_== (a.## == b.##)
      }
  }

  "matches" should {
    "ContentCoding.* always matches" in
      prop { (a: ContentCoding) =>
        ContentCoding.`*`.matches(a) must beTrue
      }
    "always matches itself" in
      prop { (a: ContentCoding) =>
        a.matches(a) must beTrue
      }
  }

  "parses" should {
    "parse plain coding" in {
      ContentCoding.parse("gzip") must_== ParseResult.success(ContentCoding.gzip)
    }
    "parse custom codings" in {
      ContentCoding.parse("mycoding") must_== ContentCoding.fromString("mycoding")
    }
    "parse with quality" in {
      ContentCoding.parse("gzip;q=0.8") must_== QValue
        .fromDouble(0.8)
        .map(qv => ContentCoding.gzip.withQValue(qv))
    }
    "fail on empty" in {
      ContentCoding.parse("") must beLeft
      ContentCoding.parse(";q=0.8") must beLeft
    }
    "parse *" in {
      ContentCoding.parse("*") must_== ParseResult.success(ContentCoding.`*`)
    }
  }

  "render" should {
    "return coding and quality" in
      prop { s: ContentCoding =>
        Renderer.renderString(s) must_== s"${s.coding.toLowerCase}${Renderer.renderString(s.qValue)}"
      }
  }

  checkAll("Order[ContentCoding]", OrderTests[ContentCoding].order)
  checkAll("HttpCodec[ContentCoding]", HttpCodecTests[ContentCoding].httpCodec)
}
