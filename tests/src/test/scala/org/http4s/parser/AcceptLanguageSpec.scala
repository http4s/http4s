package org.http4s
package parser

import org.http4s.headers.`Accept-Language`
import org.specs2.mutable.Specification
import org.http4s.{LanguageTag, QValue}

class AcceptLanguageSpec
    extends Specification
    with HeaderParserHelper[`Accept-Language`]
    with Http4s {

  def hparse(value: String): ParseResult[`Accept-Language`] =
    HttpHeaderParser.ACCEPT_LANGUAGE(value)

  val en = `Accept-Language`(LanguageTag("en"))
  val fr = `Accept-Language`(LanguageTag("fr"))
  val enq5 = `Accept-Language`(LanguageTag("en").withQuality(q(0.5)))
  val en_cool = `Accept-Language`(LanguageTag("en", "cool"))

  val all = `Accept-Language`(LanguageTag.`*`)
  val ninguno = `Accept-Language`(LanguageTag.`*`.withQuality(QValue.Zero))

  "Accept-Language" should {
    "Give correct value" in {
      en.value must be_==("en")
      enq5.value must be_==("en;q=0.5")
    }

    "Parse properly" in {
      parse(en.value) must be_==(en)
      parse(enq5.value) must be_==(enq5)
      parse(en_cool.value) must be_==(en_cool)
    }

    "Offer preferred" in {
      val unordered = `Accept-Language`(
        en.values.head.withQuality(q(0.2)),
        fr.values.head.withQuality(q(0.1)),
        en_cool.values.head)
      unordered.preferred must be_==(en_cool.values.head)
    }

    "Be satisfied correctly" in {
      all.satisfiedBy(en.values.head) must be_==(true)
      ninguno.satisfiedBy(en.values.head) must be_==(false)

      en.satisfiedBy(en_cool.values.head) must be_==(true)
      en_cool.satisfiedBy(en.values.head) must be_==(false)

      en.satisfiedBy(fr.values.head) must be_==(false)
    }
  }
}
