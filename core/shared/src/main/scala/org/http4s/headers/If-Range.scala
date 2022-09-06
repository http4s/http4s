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
import org.typelevel.ci._

// https://datatracker.ietf.org/doc/html/rfc7233#section-3.2

sealed abstract class `If-Range` extends Product with Serializable

object `If-Range` {
  final case class LastModified(date: HttpDate) extends `If-Range`
  final case class ETag(tag: EntityTag) extends `If-Range`

  def parse(s: String): ParseResult[`If-Range`] =
    ParseResult.fromParser(parser, "Invalid If-Range header")(s)

  private val parser: Parser[`If-Range`] =
    EntityTag.parser
      .eitherOr(HttpDate.parser)
      .map(_.fold(LastModified(_), ETag(_)))

  implicit val headerInstance: Header[`If-Range`, Header.Single] =
    Header.createRendered(
      ci"If-Range",
      {
        case LastModified(date) => date
        case ETag(tag) => tag
      },
      parse,
    )
}
