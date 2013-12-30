package org.http4s
package parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Header.`Accept-Encoding`
import scalaz.Validation

/**
 * @author Bryce Anderson
 *         Created on 12/30/13
 */
class AcceptEncodingSpec extends WordSpec with Matchers with HeaderParserHelper[`Accept-Encoding`] {
  def hparse(value: String): Validation[ParseErrorInfo, `Accept-Encoding`] = HttpParser.ACCEPT_ENCODING(value)

  val gzip = `Accept-Encoding`(ContentCoding.gzip)
  val gzip5 = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.5))
  val gzip55 = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.55))
  val gzip555 = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.555))

  val gzip1 = `Accept-Encoding`(ContentCoding.gzip.withQuality(1.0))

  "Accept-Encoding parser" should {

    "parse all encodings" in {
      ContentCoding.snapshot.foreach{ case (name, coding) =>
        parse(coding.value).values.head should equal(coding)
      }
    }
  }

  "Give correct value" in {
    gzip.value should equal("gzip")
    gzip5.value should equal("gzip; q=0.5")
    gzip55.value should equal("gzip; q=0.55")
    gzip555.value should equal("gzip; q=0.555")

    gzip1.value should equal("gzip")
  }

  "Parse properly" in {
    parse(gzip.value) should equal(gzip)
    parse(gzip5.value) should equal(gzip5)
    parse(gzip555.value) should equal(gzip555)

    parse("gzip; q=1.0, compress") should equal(`Accept-Encoding`(ContentCoding.gzip, ContentCoding.compress))

    parse(gzip1.value) should equal(gzip)
  }

  "Offer preferred" in {
    val unordered = `Accept-Encoding`(ContentCoding.gzip.withQuality(0.5),
      ContentCoding.compress.withQuality(0.1),
      ContentCoding.deflate)

    unordered.preferred should equal(ContentCoding.deflate)
  }

  "Be satisfied correctly" in {
    `Accept-Encoding`(ContentCoding.`*`) satisfiedBy ContentCoding.gzip should be (true)
    `Accept-Encoding`(ContentCoding.`*` withQuality 0.0) satisfiedBy ContentCoding.gzip should be (false)
    gzip satisfiedBy ContentCoding.gzip should be (true)
    gzip satisfiedBy ContentCoding.deflate should be (false)
  }
}
