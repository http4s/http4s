package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Header.`Accept-Charset`
import scalaz.Validation
import org.http4s.CharacterSet._

/**
 * @author Bryce Anderson
 *         Created on 12/26/13
 */
class AcceptCharsetSpec  extends WordSpec with Matchers with HeaderParserHelper[`Accept-Charset`] {

  def hparse(value: String): Validation[ParseErrorInfo, `Accept-Charset`] = HttpParser.ACCEPT_CHARSET(value)

  val utf = `Accept-Charset`(`UTF-8`)
  val utfq5 = `Accept-Charset`(`UTF-8`.withQuality(0.5f))
  val utfq55 = `Accept-Charset`(`UTF-8`.withQuality(0.55f))
  val utfq555 = `Accept-Charset`(`UTF-8`.withQuality(0.555f))

  val utfq1 = `Accept-Charset`(`UTF-8`.withQuality(1.0f))

  val all = `Accept-Charset`(`*`)

  "Accept-Charset" should {
    "Give correct value" in {
      utf.value should equal("UTF-8")
      utfq5.value should equal("UTF-8; q=0.5")
      utfq55.value should equal("UTF-8; q=0.55")
      utfq555.value should equal("UTF-8; q=0.555")

      utfq1.value should equal("UTF-8")
    }

    "Parse properly" in {
      parse(utf.value) should equal(utf)
      parse(utfq5.value) should equal(utfq5)
      parse(utfq555.value) should equal(utfq555)

      parse("UTF-8; q=1.0, US-ASCII") should equal(`Accept-Charset`(`UTF-8`, `US-ASCII`))

      parse(utfq1.value) should equal(utf)
    }

    "Offer preferred" in {
      val unordered = `Accept-Charset`(`UTF-16`.withQuality(0.2f), `US-ASCII`.withQuality(0.1f), `UTF-16BE`)
      unordered.preferred should equal(`UTF-16BE`)
    }

    "Be satisfied correctly" in {
      `Accept-Charset`(`*`) satisfiedBy `UTF-8` should be (true)
      `Accept-Charset`(`*` withQuality 0.0f) satisfiedBy `UTF-8` should be (false)
      `Accept-Charset`(`UTF-8`) satisfiedBy `UTF-8` should be (true)
      `Accept-Charset`(`UTF-8`) satisfiedBy `US-ASCII` should be (false)
    }
  }
}
