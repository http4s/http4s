package org.http4s
package parser

import org.http4s.headers.`Accept-Encoding`
import org.specs2.mutable.Specification

class AcceptEncodingSpec extends Specification with HeaderParserHelper[`Accept-Encoding`] {
  def hparse(value: String): ParseResult[`Accept-Encoding`] =
    HttpHeaderParser.ACCEPT_ENCODING(value)

  val gzip = `Accept-Encoding`(ContentCoding.gzip)
  val gzip5 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(0.5)))
  val gzip55 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(0.55)))
  val gzip555 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(0.555)))

  val gzip1 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(1.0)))

  "Accept-Encoding parser" should {

    "parse all encodings" in {
      foreach(ContentCoding.standard) {
        case (_, coding) =>
          parse(coding.renderString).values.head should be_==(coding)
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

    parse("gzip; q=1.0, compress") must be_==(
      `Accept-Encoding`(ContentCoding.gzip, ContentCoding.compress))

    parse(gzip1.value) must be_==(gzip)
  }
}
