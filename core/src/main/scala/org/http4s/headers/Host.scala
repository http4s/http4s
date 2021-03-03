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
import org.http4s.internal.parsing.Rfc3986
import org.http4s.util.{Renderable, Writer}
import scala.util.Try
import org.typelevel.ci.CIString

object Host {
  def apply(host: String, port: Int): Host = apply(host, Some(port))

  def parse(s: String): ParseResult[Host] =
    ParseResult.fromParser(parser, "Invalid Host")(s)

  private[http4s] val parser = {
    val port = Parser.string(":") *> Rfc3986.digit.rep.string.mapFilter { s =>
      Try(s.toInt).toOption
    }

    (Uri.Parser.host ~ port.?).map { case (host, port) =>
      Host(host.value, port)
    }
  }
  implicit val headerInstance: Header[Host, Header.Single] =
    Header.createRendered(
      CIString("Host"),
      h =>
        new Renderable {
          def render(writer: Writer): writer.type = {
            writer.append(h.host)
            if (h.port.isDefined) writer << ':' << h.port.get
            writer
          }
        },
      ParseResult.fromParser(parser, "Invalid Host header")
    )
}

/** A Request header, that provides the host and port information
  * {{{
  *   The "Host" header field in a request provides the host and port
  *   information from the target URI, enabling the origin server to
  *   distinguish among resources while servicing requests for multiple
  *   host names on a single IP address.
  * }}}
  *
  * This header was mandatory in version 1.1 of the Http protocol.
  *
  * [[https://tools.ietf.org/html/rfc7230#section-5.4 RFC-7230 Section 5.4]]
  */
final case class Host(host: String, port: Option[Int] = None) {
  def value: String = port match {
    case Some(p) => s"$host:$p"
    case None => host
  }
}
