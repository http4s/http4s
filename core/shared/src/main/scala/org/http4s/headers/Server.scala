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
import org.typelevel.ci._

object Server {

  def apply(id: ProductId, tail: ProductIdOrComment*): Server =
    apply(id, tail.toList)

  val name = ci"Server"

  def parse(s: String): ParseResult[Server] =
    ParseResult.fromParser(parser, "Invalid Server header")(s)

  private[http4s] val parser =
    ProductIdOrComment.serverAgentParser.map {
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
      parse
    )

}

/** Server header
  * https://tools.ietf.org/html/rfc7231#section-7.4.2
  */
final case class Server(product: ProductId, rest: List[ProductIdOrComment])
