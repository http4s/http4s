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
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci.CIString

import scala.annotation.nowarn

object Server extends HeaderCompanion[Server]("Server") {

  override val name: CIString = super.name

  def apply(id: ProductId, tail: ProductIdOrComment*): Server =
    apply(id, tail.toList)

  @nowarn("cat=deprecation")
  @deprecated("Use parse(Int) instead", "0.23.17")
  override def parse(s: String): ParseResult[`Server`] =
    parse(CommonRules.CommentDefaultMaxDepth)(s)

  def parse(maxDepth: Int)(s: String): ParseResult[`Server`] =
    parsePartiallyApplied(maxDepth)(s)

  private def parsePartiallyApplied(maxDepth: Int): String => ParseResult[`Server`] =
    ParseResult.fromParser(parser(maxDepth), "Invalid Server header")

  @deprecated("Use parser(Int) instead", "0.23.17")
  private[http4s] val parser: Parser[Server] =
    parser(Rfc7230.CommentDefaultMaxDepth)

  private[http4s] def parser(maxDepth: Int): Parser[Server] =
    ProductIdOrComment.serverAgentParser(maxDepth).map {
      case (product: ProductId, tokens: List[ProductIdOrComment]) =>
        Server(product, tokens)
    }

  implicit val headerInstance: Header[Server, Header.Single] =
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
}

/** Server header
  * https://datatracker.ietf.org/doc/html/rfc7231#section-7.4.2
  */
final case class Server(product: ProductId, rest: List[ProductIdOrComment])
