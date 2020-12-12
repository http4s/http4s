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
