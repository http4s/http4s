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

import org.http4s.util.{Renderable, Writer}
import org.typelevel.ci.CIString

object `User-Agent` extends HeaderKey.Internal[`User-Agent`] with HeaderKey.Singleton {
  def apply(id: ProductId): `User-Agent` =
    new `User-Agent`(id, Nil)

  override def parse(s: String): ParseResult[`User-Agent`] =
    ParseResult.fromParser(parser, "Invalid User-Agent header")(s)

  private[http4s] val parser =
    ProductIdOrComment.serverAgentParser.map {
      case (product: ProductId, tokens: List[ProductIdOrComment]) =>
        `User-Agent`(product, tokens)
    }

  implicit val headerInstance: v2.Header[`User-Agent`, v2.Header.Single] =
    v2.Header.createRendered(
      CIString("User-Agent"),
      h =>
        new Renderable {
          def render(writer: Writer): writer.type = {
            writer << h.product
            h.rest.foreach {
              case p: ProductId => writer << ' ' << p
              case ProductComment(c) => writer << ' ' << '(' << c << ')'
            }
            writer
          }

        },
      ParseResult.fromParser(parser, "Invalid User-Agent header")
    )

}

/** User-Agent header
  * [[https://tools.ietf.org/html/rfc7231#section-5.5.3 RFC-7231 Section 5.5.3]]
  */
final case class `User-Agent`(product: ProductId, rest: List[ProductIdOrComment])
    extends Header.Parsed {
  def key: `User-Agent`.type = `User-Agent`

  override def renderValue(writer: Writer): writer.type = {
    writer << product
    rest.foreach {
      case p: ProductId => writer << ' ' << p
      case ProductComment(c) => writer << ' ' << '(' << c << ')'
    }
    writer
  }
}
