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
import org.http4s.util.{Renderer, Writer}

object `If-Unmodified-Since`
    extends HeaderKey.Internal[`If-Unmodified-Since`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`If-Unmodified-Since`] =
    ParseResult.fromParser(parser, "Invalid If-Unmodified-Since header")(s)

  /* `If-Modified-Since = HTTP-date` */
  private[http4s] val parser: Parser1[`If-Unmodified-Since`] =
    HttpDate.parser.map(apply)
}

final case class `If-Unmodified-Since`(date: HttpDate) extends Header.Parsed {
  override def key: `If-Unmodified-Since`.type = `If-Unmodified-Since`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
