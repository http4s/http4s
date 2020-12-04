/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.parse.Parser1
import cats.syntax.all._
import org.http4s.util.{Renderer, Writer}

object `If-Unmodified-Since`
    extends HeaderKey.Internal[`If-Unmodified-Since`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`If-Unmodified-Since`] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid If-Unmodified-Since header", e.toString)
    }

  /* `If-Modified-Since = HTTP-date` */
  private[http4s] val parser: Parser1[`If-Unmodified-Since`] =
    HttpDate.parser.map(apply)
}

final case class `If-Unmodified-Since`(date: HttpDate) extends Header.Parsed {
  override def key: `If-Unmodified-Since`.type = `If-Unmodified-Since`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
