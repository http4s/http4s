package org.http4s
package headers

import scala.annotation.tailrec
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{NonEmptyList, Writer}

object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] {
  def from(headers: Headers): List[`Set-Cookie`] =
    headers.toList.map(matchHeader).collect {
      case Some(h) => h
    }

  def unapply(headers: Headers): Option[NonEmptyList[`Set-Cookie`]] =
    from(headers) match {
      case Nil => None
      case h :: t => Some(NonEmptyList(h, t:_*))
    }

  override def parse(s: String): ParseResult[`Set-Cookie`] =
    HttpHeaderParser.SET_COOKIE(s)
}

final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends Header.Parsed {
  override def key: `Set-Cookie`.type = `Set-Cookie`
  override def renderValue(writer: Writer): writer.type = cookie.render(writer)
}

