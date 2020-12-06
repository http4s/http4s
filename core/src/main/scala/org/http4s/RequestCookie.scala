/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.parse.Parser1
import org.http4s.internal.parsing.Rfc6265
import org.http4s.util.{Renderable, Writer}

// see http://tools.ietf.org/html/rfc6265
final case class RequestCookie(name: String, content: String) extends Renderable {
  override lazy val renderString: String = super.renderString

  override def render(writer: Writer): writer.type = {
    writer.append(name).append('=').append(content)
    writer
  }
}

object RequestCookie {
  private[http4s] val parser: Parser1[RequestCookie] =
    Rfc6265.cookiePair.map { case (name, value) =>
      RequestCookie(name, value)
    }
}
