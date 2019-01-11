package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser
import org.http4s.ResponseCookie
import org.http4s.util.Writer

object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] {
  def from(headers: Headers): List[`Set-Cookie`] =
    headers.toList.map(matchHeader).collect {
      case Some(h) => h
    }

  def unapply(headers: Headers): Option[NonEmptyList[`Set-Cookie`]] =
    from(headers) match {
      case Nil => None
      case h :: t => Some(NonEmptyList(h, t))
    }

  override def parse(s: String): ParseResult[`Set-Cookie`] =
    HttpHeaderParser.SET_COOKIE(s)
}

final case class `Set-Cookie`(cookie: ResponseCookie) extends Header.Parsed {
  override def key: `Set-Cookie`.type = `Set-Cookie`
  override def renderValue(writer: Writer): writer.type = cookie.render(writer)
}
