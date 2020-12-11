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

import cats.parse.Parser
import cats.syntax.all._
import org.http4s.util.{Renderer, Writer}

object Expires extends HeaderKey.Internal[Expires] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Expires] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Expires header", e.toString)
    }

  /* `Expires = HTTP-date` */
  private[http4s] val parser: Parser[Expires] = {
    import Parser.anyChar

    def httpDate = HttpDate.parser

    // A cache recipient MUST interpret invalid date formats, especially the
    // value "0", as representing a time in the past (i.e., "already
    // expired").
    def invalid = anyChar.rep.as(HttpDate.Epoch)

    httpDate.orElse(invalid).map(apply)
  }
}

/** A Response header that _gives the date/time after which the response is considered stale_.
  *
  * The HTTP RFCs indicate that Expires should be in the range of now to 1 year in the future.
  * However, it is a usual practice to set it to the past of far in the future
  * Thus any instant is in practice allowed
  *
  * [[https://tools.ietf.org/html/rfc7234#section-5.3 RFC-7234 Section 5.3]]
  *
  * @param expirationDate the date of expiration. The RFC has a warning, that using large values
  * can cause problems due to integer or clock overflows.
  */
final case class Expires(expirationDate: HttpDate) extends Header.Parsed {
  val key = `Expires`
  override val value = Renderer.renderString(expirationDate)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
