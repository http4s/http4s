package org.http4s
package parser

import org.http4s.Header.`Accept-Encoding`
import org.specs2.mutable.Specification
import scalaz.Validation

class AcceptEncodingSpec extends Specification with HeaderParserHelper[`Accept-Encoding`] {
  def hparse(value: String): Validation[ParseErrorInfo, `Accept-Encoding`] = HttpParser.ACCEPT_ENCODING(value)

  val gzip = `Accept-Encoding`(ContentCoding.gzip)
  val gzip5 = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.5))
  val gzip55 = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.55))
  val gzip555 = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.555))

  val gzip1 = `Accept-Encoding`(ContentCoding.gzip.withQuality(1.0))

  "Accept-Encoding parser" should {

    "parse all encodings" in {
      foreach(ContentCoding.snapshot) { case (name, coding) =>
        parse(coding.value).values.head should be_==(coding)
      }
    }
  }

  "Give correct value" in {
    gzip.value must be_==("gzip")
    gzip5.value must be_==("gzip;q=0.5")
    gzip55.value must be_==("gzip;q=0.55")
    gzip555.value must be_==("gzip;q=0.555")

    gzip1.value must be_==("gzip")
  }

  "Parse properly" in {
    parse(gzip.value) must be_==(gzip)
    parse(gzip5.value) must be_==(gzip5)
    parse(gzip555.value) must be_==(gzip555)

    parse("gzip; q=1.0, compress") must be_==(`Accept-Encoding`(ContentCoding.gzip, ContentCoding.compress))

    parse(gzip1.value) must be_==(gzip)
  }

  "Offer preferred" in {
    val unordered = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.5),
      ContentCoding.compress.withQuality(0.1),
      ContentCoding.deflate)

    unordered.preferred must be_==(ContentCoding.deflate)
  }

  "Be satisfied correctly" in {
    `Accept-Encoding`(ContentCoding.`*`) satisfiedBy ContentCoding.gzip should beTrue
    `Accept-Encoding`(ContentCoding.`*` withQuality 0.0) satisfiedBy ContentCoding.gzip should beFalse
    gzip satisfiedBy ContentCoding.gzip should beTrue
    gzip satisfiedBy ContentCoding.deflate should beFalse
  }
}
