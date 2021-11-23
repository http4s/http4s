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

import cats.data.NonEmptyList
import cats.parse.Parser
import cats.parse.Parser0
import cats.parse.Rfc5234
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

sealed abstract class Origin

object Origin {
  // An Origin header may be the string "null", representing an "opaque origin":
  // https://stackoverflow.com/questions/42239643/when-does-firefox-set-the-origin-header-to-null-in-post-requests
  case object Null extends Origin

  // If the Origin is not "null", it is a non-empty list of Hosts:
  // https://datatracker.ietf.org/doc/html/rfc6454#section-7
  final case class HostList(hosts: NonEmptyList[Host]) extends Origin

  // A host in an Origin header isn't a full URI.
  // It only contains a scheme, a host, and an optional port.
  // Hence we re-used parts of the Uri class here, but we don't use a whole Uri:
  // https://datatracker.ietf.org/doc/html/rfc6454#section-7
  final case class Host(scheme: Uri.Scheme, host: Uri.Host, port: Option[Int] = None)
      extends Renderable {
    def toUri: Uri =
      Uri(scheme = Some(scheme), authority = Some(Uri.Authority(host = host, port = port)))

    def render(writer: Writer): writer.type =
      toUri.render(writer)
  }

  private[http4s] val nullHostParser: Parser0[Origin.Null.type] = {
    import Parser.{`end`, string}

    (string("null") *> `end`).as(Origin.Null)
  }

  private[http4s] val singleHostParser: Parser[Origin.Host] = {
    import Parser.{char, string}
    import Rfc5234.digit

    val scheme = Uri.Parser.scheme

    val host = Uri.Parser.host

    val port = char(':') *> digit.rep.string.map(_.toInt)

    ((scheme <* string("://")) ~ host ~ port.? ~ char('/').?).map { case (((sch, host), port), _) =>
      Origin.Host(sch, host, port)
    }
  }

  private[http4s] val hostListParser: Parser[NonEmptyList[Origin.Host]] = {
    import cats.parse.Rfc5234.sp

    singleHostParser.repSep(sp)
  }

  private[http4s] val parser: Parser0[Origin] =
    nullHostParser.orElse(hostListParser.map(hosts => Origin.HostList(hosts)))

  def parse(s: String): ParseResult[Origin] =
    ParseResult.fromParser(parser, "Invalid Origin header")(s)

  implicit val headerInstance: Header[Origin, Header.Single] =
    Header.createRendered(
      ci"Origin",
      v =>
        new Renderable {
          def render(writer: Writer): writer.type =
            v match {
              case HostList(hosts) =>
                writer << hosts.head
                hosts.tail.foreach { host =>
                  writer << " "
                  writer << host
                }
                writer
              case Null => writer << "null"
            }

        },
      parse,
    )

}
