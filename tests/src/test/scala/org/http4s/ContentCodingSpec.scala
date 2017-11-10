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
        ((a.coding, a.qValue), (b.coding, b.qValue)) match {
          case ((ac, aq), (bc, bq)) if ac == bc => aq.compareTo(bq) must_== a.compare(b)
          case ((ac, _), (bc, _)) if ac.compareToIgnoreCase(bc) > 0 => a.compare(b) must be_>(0)
          case _ => a.compare(b) must be_<(0)
        }
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
      ContentCoding.parse("gzip;q=0.8") must_== Right(ContentCoding.gzip.withQValue(q(0.8)))
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
