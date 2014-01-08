package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Header.`Accept-Language`
import scalaz.Validation
import org.http4s.{Q, LanguageTag}

/**
 * @author Bryce Anderson
 *         Created on 1/7/14
 */
class AcceptLanguageSpec  extends WordSpec with Matchers with HeaderParserHelper[`Accept-Language`] {

  def hparse(value: String): Validation[ParseErrorInfo, `Accept-Language`] = HttpParser.ACCEPT_LANGUAGE(value)

  val en = `Accept-Language`(LanguageTag("en"))
  val fr = `Accept-Language`(LanguageTag("fr"))
  val enq5 = `Accept-Language`(LanguageTag("en").withQuality(0.5))
  val en_cool = `Accept-Language`(LanguageTag("en", "cool"))

  val all = `Accept-Language`(LanguageTag.`*`)
  val none = `Accept-Language`(LanguageTag.`*`.withQuality(0))

  "Accept-Language" should {
    "Give correct value" in {
      en.value should equal("en")
      enq5.value should equal("en; q=0.5")
    }

    "Parse properly" in {
      parse(en.value) should equal(en)
      parse(enq5.value) should equal(enq5)
      println(en_cool.values)
      println(parse(en_cool.value).values)
      parse(en_cool.value) should equal(en_cool)
    }

    "Offer preferred" in {
      val unordered = `Accept-Language`(en.values.head.withQuality(0.2),
                                        fr.values.head.withQuality(0.1),
                                        en_cool.values.head)
      unordered.preferred should equal(en_cool.values.head)
    }

    "Be satisfied correctly" in {
      all satisfiedBy en.values.head should equal (true)
      none satisfiedBy en.values.head should equal (false)

      en satisfiedBy en_cool.values.head should equal (true)
      en_cool satisfiedBy en.values.head should equal (false)

      en satisfiedBy fr.values.head should equal (false)
    }
  }
}