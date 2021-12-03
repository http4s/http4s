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
import cats.parse.Parser.char
import cats.parse.Parser.end
import cats.parse.Parser.string
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

sealed abstract class Origin

object Origin {
  // An Origin header may be the string "null", representing an "opaque origin":
  // https://stackoverflow.com/questions/42239643/when-does-firefox-set-the-origin-header-to-null-in-post-requests
  case object `null` extends Origin

  // A host in an Origin header isn't a full URI.
  // It only contains a scheme, a host, and an optional port.
  // Hence we re-used parts of the Uri class here, but we don't use a whole Uri:
  // http://tools.ietf.org/html/rfc6454#section-7
  final case class Host(scheme: Uri.Scheme, host: Uri.Host, port: Option[Int] = None)
      extends Origin
      with Renderable {
    def toUri: Uri =
      Uri(scheme = Some(scheme), authority = Some(Uri.Authority(host = host, port = port)))

    def render(writer: Writer): writer.type =
      toUri.render(writer)
  }

  private[http4s] val parser: Parser[Origin] = {
    import Uri.Parser.{host, port, scheme}

    val nullP = (string("null") *> `end`).as(Origin.`null`)

    val hostP = ((scheme <* string("://")) ~ host ~ (char(':') *> port).?.map(_.flatten)).map {
      case ((sch, host), port) => Origin.Host(sch, host, port)
    }

    nullP | hostP
  }

  def parse(s: String): ParseResult[Origin] =
    ParseResult.fromParser(parser, "Invalid Origin header")(s)

  implicit val headerInstance: Header[Origin, Header.Single] =
    Header.createRendered(
      ci"Origin",
      v =>
        new Renderable {
          def render(writer: Writer): writer.type =
            v match {
              case h: Host => writer << h
              case `null` => writer << "null"
            }
        },
      parse,
    )
}
