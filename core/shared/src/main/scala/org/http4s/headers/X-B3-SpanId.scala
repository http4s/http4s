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

import cats.Applicative
import cats.parse.{Parser0, Rfc5234}
import org.http4s.parser.ZipkinHeader
import org.http4s.util.{Renderable, Writer}
import org.http4s.Header
import org.typelevel.ci._

object `X-B3-SpanId` {

  def parse(s: String): ParseResult[`X-B3-SpanId`] =
    ParseResult.fromParser(parser, "Invalid X-B3-SpanId header")(s)

  private[http4s] val parser: Parser0[`X-B3-SpanId`] =
    Applicative[Parser0]
      .replicateA(16, Rfc5234.hexdig)
      .string
      .map(ZipkinHeader.idStringToLong)
      .map(`X-B3-SpanId`.apply)

  implicit val headerInstance: Header[`X-B3-SpanId`, Header.Single] =
    Header.createRendered(
      ci"X-B3-SpanId",
      h =>
        new Renderable {
          def render(writer: Writer): writer.type =
            xB3RenderValueImpl(writer, h.id)

        },
      parse
    )

}

final case class `X-B3-SpanId`(id: Long)
