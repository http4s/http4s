package org.http4s.headers

import org.http4s.Header.Raw
import org.http4s.{Header, HeaderKey}
import org.http4s.parser.BaseCookieParser
import org.http4s.util.Writer
import org.parboiled2._

import scalaz.NonEmptyList

object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[Cookie] =
    new CookieParser(raw.value).parse.toOption

  private class CookieParser(input: ParserInput) extends BaseCookieParser[Cookie](input) {
    def entry: Rule1[Cookie] = rule {
      oneOrMore(CookiePair).separatedBy(";" ~ OptWS) ~ EOI ~> {xs: Seq[org.http4s.Cookie] => Cookie(xs.head, xs.tail: _*)}
    }
  }
}

final case class Cookie(values: NonEmptyList[org.http4s.Cookie]) extends Header.RecurringRenderable {
  override def key = Cookie
  type Value = org.http4s.Cookie
  override def renderValue(writer: Writer): writer.type = {
    values.head.render(writer)
    values.tail.foreach( writer << "; " << _ )
    writer
  }
}

