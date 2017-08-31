package org.http4s
package parser

import org.http4s.headers._
import org.specs2.mutable.Specification

class ProxyAuthenticateHeaderSpec
    extends Specification
    with HeaderParserHelper[`Proxy-Authenticate`] {
  def hparse(value: String): ParseResult[`Proxy-Authenticate`] =
    HttpHeaderParser.PROXY_AUTHENTICATE(value)

  override def parse(value: String) =
    hparse(value).fold(err => sys.error(s"Couldn't parse: $value"), identity)

  val params = Map("a" -> "b", "c" -> "d")
  val c = Challenge("Basic", "foo")

  val str = "Basic realm=\"foo\""

  val wparams = c.copy(params = params)

  "Proxy-Authenticate Header parser" should {
    "Render challenge correctly" in {
      c.renderString must be_==(str)
    }

    "Parse a basic authentication" in {
      parse(str) must be_==(`Proxy-Authenticate`(c))
    }

    "Parse a basic authentication with params" in {
      parse(wparams.renderString) must be_==(`Proxy-Authenticate`(wparams))
    }

    "Parse multiple concatenated authentications" in {
      val twotypes = "Newauth realm=\"apps\", Basic realm=\"simple\""
      val twoparsed = Challenge("Newauth", "apps") :: Challenge("Basic", "simple") :: Nil

      parse(twotypes).values.toList must be_==(twoparsed)
    }

    "parse mulmultiple concatenated authentications with params" in {
      val twowparams =
        "Newauth realm=\"apps\", type=1, title=\"Login to apps\", Basic realm=\"simple\""
      val twp = Challenge("Newauth", "apps", Map("type" -> "1", "title" -> "Login to apps")) ::
        Challenge("Basic", "simple") :: Nil

      parse(twowparams).values.toList must be_==(twp)
    }
  }
}
