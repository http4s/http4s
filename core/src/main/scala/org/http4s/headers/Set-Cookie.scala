/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
