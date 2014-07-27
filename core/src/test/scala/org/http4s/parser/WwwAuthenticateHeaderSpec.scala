package org.http4s.parser

import org.http4s.Header.`WWW-Authenticate`
import org.specs2.mutable.Specification
import scalaz.Validation
import org.http4s.Challenge
import scala.Predef._
import org.http4s.Challenge

class WwwAuthenticateHeaderSpec extends Specification with HeaderParserHelper[`WWW-Authenticate`] {
  def hparse(value: String): Validation[ParseErrorInfo, `WWW-Authenticate`] = HttpParser.WWW_AUTHENTICATE(value)

  override def parse(value: String) =  hparse(value).fold(err => sys.error(s"Couldn't parse: $value"), identity)

  val params = Map("a"->"b", "c"->"d")
  val c = Challenge("Basic", "foo")

  val str= "Basic realm=\"foo\""

  val wparams = c.copy(params = params)

  "WWW-Authenticate Header parser" should {
    "Render challenge correctly" in {
      c.value must be_==(str)
    }

    "Parse a basic authentication" in {
      parse(str) must be_==(`WWW-Authenticate`(c))
    }

    "Parse a basic authentication with params" in {
      parse(wparams.value) must be_==(`WWW-Authenticate`(wparams))
    }

    "Parse multiple concatenated authentications" in {
      val twotypes = "Newauth realm=\"apps\", Basic realm=\"simple\""
      val twoparsed = Challenge("Newauth", "apps")::Challenge("Basic","simple")::Nil

      parse(twotypes).values.list must be_==(twoparsed)
    }

    "parse mulmultiple concatenated authentications with params" in {
      val twowparams = "Newauth realm=\"apps\", type=1, title=\"Login to apps\", Basic realm=\"simple\""
      val twp = Challenge("Newauth", "apps", Map("type"->"1","title"->"Login to apps"))::
        Challenge("Basic","simple")::Nil

      parse(twowparams).values.list must be_==(twp)
    }
  }
}
