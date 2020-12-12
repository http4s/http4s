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

import cats.parse.Parser1
import cats.syntax.all._
import org.http4s.util.{Renderer, Writer}

object `If-Modified-Since`
    extends HeaderKey.Internal[`If-Modified-Since`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`If-Modified-Since`] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid If-Modified-Since header", e.toString)
    }

  /* `If-Modified-Since = HTTP-date` */
  private[http4s] val parser: Parser1[`If-Modified-Since`] =
    HttpDate.parser.map(apply)
}

/** {{
  *   The "If-Modified-Since" header field makes a GET or HEAD request
  *   method conditional on the selected representation's modification date
  *   being more recent than the date provided in the field-value.
  * }}
  *
  * [[https://tools.ietf.org/html/rfc7232#section-3.3 RFC-7232]]
  */
final case class `If-Modified-Since`(date: HttpDate) extends Header.Parsed {
  override def key: `If-Modified-Since`.type = `If-Modified-Since`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
