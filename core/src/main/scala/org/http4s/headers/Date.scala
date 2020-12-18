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

object Date extends HeaderKey.Internal[Date] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Date] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Date header", e.toString)
    }

  /* `Date = HTTP-date` */
  private[http4s] val parser: Parser1[`Date`] =
    HttpDate.parser.map(apply)
}

final case class Date(date: HttpDate) extends Header.Parsed {
  def key: Date.type = Date
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
