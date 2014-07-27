package org.http4s.parser

import org.http4s.Header.`Accept-Charset`
import org.specs2.mutable.Specification
import scalaz.Validation
import org.http4s.CharacterSet._

class AcceptCharsetSpec extends Specification with HeaderParserHelper[`Accept-Charset`] {

  def hparse(value: String): Validation[ParseErrorInfo, `Accept-Charset`] = HttpParser.ACCEPT_CHARSET(value)

  val utf = `Accept-Charset`(`UTF-8`)
  val utfq5 = `Accept-Charset`(`UTF-8`.withQuality(0.5))
  val utfq55 = `Accept-Charset`(`UTF-8`.withQuality(0.55))
  val utfq555 = `Accept-Charset`(`UTF-8`.withQuality(0.555))

  val utfq1 = `Accept-Charset`(`UTF-8`.withQuality(1.0))

  val all = `Accept-Charset`(`*`)

  "Accept-Charset" should {
    "Give correct value" in {
      utf.value must be_==("UTF-8")
      utfq5.value must be_==("UTF-8; q=0.5")
      utfq55.value must be_==("UTF-8; q=0.55")
      utfq555.value must be_==("UTF-8; q=0.555")

      utfq1.value must be_==("UTF-8")
    }

    "Parse properly" in {
      parse(utf.value) must be_==(utf)
      parse(utfq5.value) must be_==(utfq5)
      parse(utfq555.value) must be_==(utfq555)

      parse("UTF-8; q=1.0, US-ASCII") must be_==(`Accept-Charset`(`UTF-8`, `US-ASCII`))

      parse(utfq1.value) must be_==(utf)
    }

    "Offer preferred" in {
      val unordered = `Accept-Charset`(`UTF-16`.withQuality(0.2f), `US-ASCII`.withQuality(0.1f), `UTF-16BE`)
      unordered.preferred must be_==(`UTF-16BE`)
    }

    "Be satisfied correctly" in {
      `Accept-Charset`(`*`) satisfiedBy `UTF-8` should beTrue
      `Accept-Charset`(`*` withQuality 0.0) satisfiedBy `UTF-8` should beFalse
      `Accept-Charset`(`UTF-8`) satisfiedBy `UTF-8` should beTrue
      `Accept-Charset`(`UTF-8`) satisfiedBy `US-ASCII` should beFalse
    }
  }
}
