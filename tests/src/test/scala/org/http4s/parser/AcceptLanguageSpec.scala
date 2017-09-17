package org.http4s
package parser

import org.http4s.headers.`Accept-Language`
import org.specs2.mutable.Specification
import scalaz.Validation
import org.http4s.{QValue, LanguageTag}

class AcceptLanguageSpec extends Specification with HeaderParserHelper[`Accept-Language`] with Http4s {

  def hparse(value: String): ParseResult[`Accept-Language`] = HttpHeaderParser.ACCEPT_LANGUAGE(value)

  val en = `Accept-Language`(LanguageTag("en"))
  val fr = `Accept-Language`(LanguageTag("fr"))
  val enq5 = `Accept-Language`(LanguageTag("en").withQValue(q(0.5)))
  val en_cool = `Accept-Language`(LanguageTag("en", "cool"))

  val all = `Accept-Language`(LanguageTag.`*`)
  val ninguno = `Accept-Language`(LanguageTag.`*`.withQValue(QValue.Zero))

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
  }
}
