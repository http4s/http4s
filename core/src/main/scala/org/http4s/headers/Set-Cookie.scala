/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.parse.Parser1
import cats.syntax.all._
import org.http4s.util.Writer

object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] {
  def from(headers: Headers): List[`Set-Cookie`] =
    headers.toList.map(matchHeader).collect { case Some(h) =>
      h
    }

  def unapply(headers: Headers): Option[NonEmptyList[`Set-Cookie`]] =
    from(headers) match {
      case Nil => None
      case h :: t => Some(NonEmptyList(h, t))
    }

  override def parse(s: String): ParseResult[`Set-Cookie`] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Set-Cookie header", e.toString)
    }

  /* set-cookie-header = "Set-Cookie:" SP set-cookie-string */
  private[http4s] val parser: Parser1[`Set-Cookie`] =
    ResponseCookie.parser.map(`Set-Cookie`(_))
}

final case class `Set-Cookie`(cookie: ResponseCookie) extends Header.Parsed {
  override def key: `Set-Cookie`.type = `Set-Cookie`
  override def renderValue(writer: Writer): writer.type = cookie.render(writer)
}
