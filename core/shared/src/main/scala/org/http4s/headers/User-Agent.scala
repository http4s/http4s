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

import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderable
import org.http4s.util.Renderer
import org.http4s.util.Writer
import org.typelevel.ci._

object `User-Agent` {

  def apply(id: ProductId, tail: ProductIdOrComment*): `User-Agent` =
    apply(id, tail.toList)

  val name = ci"User-Agent"

  @deprecated("Use parse(Int)(String) instead", "0.23.17")
  def parse(s: String): ParseResult[`User-Agent`] =
    parse(CommonRules.CommentDefaultMaxDepth)(s)

  def parse(maxDepth: Int)(s: String): ParseResult[`User-Agent`] =
    parsePartiallyApplied(maxDepth)(s)

  private def parsePartiallyApplied(maxDepth: Int): String => ParseResult[`User-Agent`] =
    ParseResult.fromParser(parser(maxDepth), "Invalid User-Agent header")

  @deprecated("Use parser(Int) instead", "0.23.17")
  private[http4s] val parser =
    ProductIdOrComment.serverAgentParser.map {
      case (product: ProductId, tokens: List[ProductIdOrComment]) =>
        `User-Agent`(product, tokens)
    }

  private[http4s] def parser(maxDepth: Int) =
    ProductIdOrComment.serverAgentParser(maxDepth).map {
      case (product: ProductId, tokens: List[ProductIdOrComment]) =>
        `User-Agent`(product, tokens)
    }

  implicit val headerInstance: Header[`User-Agent`, Header.Single] =
    Header.createRendered(
      name,
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
      parsePartiallyApplied(CommonRules.CommentDefaultMaxDepth),
    )

  implicit def convert(implicit
      select: Header.Select.Aux[`User-Agent`, cats.Id]
  ): Renderer[`User-Agent`] =
    new Renderer[`User-Agent`] {
      override def render(writer: Writer, t: `User-Agent`): writer.type = writer << select.toRaw(t)
    }

}

/** User-Agent header
  * [[https://datatracker.ietf.org/doc/html/rfc7231#section-5.5.3 RFC-7231 Section 5.5.3]]
  */
final case class `User-Agent`(product: ProductId, rest: List[ProductIdOrComment])
