/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.parse.{Parser, Parser1}
import cats.syntax.all._
import org.http4s.util.Writer

object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Cookie] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Cookie header", e.toString)
    }

  private[http4s] val parser: Parser1[Cookie] = {
    import Parser.{string1}

    /* cookie-string = cookie-pair *( ";" SP cookie-pair ) */
    (RequestCookie.parser ~ (string1("; ") *> RequestCookie.parser).rep).map { case (head, tail) =>
      Cookie(NonEmptyList(head, tail))
    }
  }
}

final case class Cookie(values: NonEmptyList[RequestCookie]) extends Header.RecurringRenderable {
  override def key: Cookie.type = Cookie
  type Value = RequestCookie
  override def renderValue(writer: Writer): writer.type = {
    values.head.render(writer)
    values.tail.foreach(writer << "; " << _)
    writer
  }
}
