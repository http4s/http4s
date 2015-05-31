package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.BaseCookieParser
import org.http4s.util.Writer
import org.parboiled2._

object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`Set-Cookie`] =
    new SetCookieParser(raw.value).parse.toOption

  private class SetCookieParser(input: ParserInput) extends BaseCookieParser[`Set-Cookie`](input) {
    def entry: Rule1[`Set-Cookie`] = rule {
      CookiePair ~ zeroOrMore(";" ~ OptWS ~ CookieAttrs) ~ EOI ~> (`Set-Cookie`(_))
    }
  }
}

final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends Header.Parsed {
  override def key = `Set-Cookie`
  override def renderValue(writer: Writer): writer.type = cookie.render(writer)
}

