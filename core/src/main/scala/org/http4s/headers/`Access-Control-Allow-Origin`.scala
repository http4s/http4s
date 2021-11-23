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

import cats.data.NonEmptyList
import cats.parse.Parser
import cats.parse.Parser0
import org.http4s.Header
import org.http4s.ParseResult
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

/** That header indicates whether the response to a CORS request can be shared,
  * via returning the literal value of the list of [[Origin.Host]] origins,
  * `null` or `*` in a response.
  *
  * @see [[https://fetch.spec.whatwg.org/#http-access-control-allow-origin CORS protocol, HTTP responses, Access-Control-Allow-Origin]].
  */
sealed abstract class `Access-Control-Allow-Origin` extends Product with Serializable

object `Access-Control-Allow-Origin` {

  case object `null` extends `Access-Control-Allow-Origin` {
    override val toString: String = "null"
  }

  case object wildcard extends `Access-Control-Allow-Origin` {
    override val toString: String = "wildcard"
  }

  final case class HostList(hosts: NonEmptyList[Origin.Host])
      extends `Access-Control-Allow-Origin`
      with Renderable {
    def render(writer: Writer): writer.type = {
      writer << hosts.head
      hosts.tail.foreach { host =>
        writer << " "
        writer << host
      }
      writer
    }
  }

  private[http4s] val parser: Parser0[`Access-Control-Allow-Origin`] = {
    import Parser.{`end`, string}

    val nullHost = (string(`null`.toString) *> `end`).as(`Access-Control-Allow-Origin`.`null`)
    val wildCardHost =
      (string(wildcard.toString) *> `end`).as(`Access-Control-Allow-Origin`.`wildcard`)

    wildCardHost.orElse(nullHost).orElse(Origin.hostListParser.map(HostList))
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
              case `null` =>
                writer << `null`.toString
              case `wildcard` =>
                writer << wildcard.toString
              case host: HostList =>
                writer << host
            }
        },
      parse,
    )
}
