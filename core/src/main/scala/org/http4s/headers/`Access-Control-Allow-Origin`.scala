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

package org.http4s.headers

import cats.parse.Parser
import cats.parse.Parser0
import org.http4s.Header
import org.http4s.ParseResult
import org.http4s.Uri
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

/** That header indicates whether the response to a CORS request can be shared,
  * via returning the literal value of the [[Origin.Host]] request header,
  * `null` or `*` in a response.
  *
  * For more details, see https://fetch.spec.whatwg.org/#http-access-control-allow-origin.
  */
sealed abstract class `Access-Control-Allow-Origin` extends Product with Serializable

object `Access-Control-Allow-Origin` {

  case object Null extends `Access-Control-Allow-Origin`

  case object Wildcard extends `Access-Control-Allow-Origin`

  final case class Host(origin: Origin.Host) extends `Access-Control-Allow-Origin` with Renderable {
    def toUri: Uri =
      Uri(
        scheme = Some(origin.scheme),
        authority = Some(Uri.Authority(host = origin.host, port = origin.port)),
      )

    def render(writer: Writer): writer.type =
      toUri.render(writer)
  }

  /** Based on the [[Origin.Host]] header parser. */
  private[http4s] val parser: Parser0[`Access-Control-Allow-Origin`] = {
    import Parser.{`end`, string}

    val nullHost = (string("null") *> `end`).as(`Access-Control-Allow-Origin`.Null)
    val wildCardHost = (string("*") *> `end`).as(`Access-Control-Allow-Origin`.Wildcard)

    wildCardHost.orElse(nullHost).orElse(Origin.singleHostParser.map(Host))
  }

  def parse(s: String): ParseResult[`Access-Control-Allow-Origin`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Origin header")(s)

  val name: CIString = ci"Access-Control-Allow-Origin"

  implicit val headerInstance: Header[`Access-Control-Allow-Origin`, Header.Single] =
    Header.createRendered(
      name,
      v =>
        new Renderable {
          def render(writer: Writer): writer.type =
            v match {
              case `Access-Control-Allow-Origin`.Null =>
                writer << "null"
              case `Access-Control-Allow-Origin`.Wildcard =>
                writer << "*"
              case host: `Access-Control-Allow-Origin`.Host =>
                writer << host
            }
        },
      parse,
    )
}
