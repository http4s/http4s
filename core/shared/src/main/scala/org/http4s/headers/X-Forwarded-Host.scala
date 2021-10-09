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

object `X-Forwarded-Host` extends HeaderCompanion[`X-Forwarded-Host`]("X-Forwarded-Host") {
  def apply(host: String, port: Int): `X-Forwarded-Host` = apply(host, Some(port))

  private[http4s] val parser = {
    val port = Parser.string(":") *> Rfc3986.digit.rep.string.mapFilter { s =>
      Try(s.toInt).toOption
    }

    (Uri.Parser.host ~ port.?).map { case (host, port) =>
      `X-Forwarded-Host`(host.value, port)
    }
  }
  implicit val headerInstance: Header[`X-Forwarded-Host`, Header.Single] =
    createRendered { h =>
      new Renderable {
        def render(writer: Writer): writer.type = {
          writer.append(h.host)
          if (h.port.isDefined) writer << ':' << h.port.get
          writer
        }
      }
    }
}

/** A Request header, that provides the host and port information
  * {{{
  *   The "host" parameter is used to forward the original value of the
  *   "Host" header field.  This can be used, for example, by the origin
  *   server if a reverse proxy is rewriting the "Host" header field to
  *   some internal host name.
  *
  *   The syntax for a "host" value, after potential quoted-string
  *   unescaping, MUST conform to the Host ABNF described in Section 5.4 of
  *   [RFC7230].
  * }}}
  *
  */
final case class `X-Forwarded-Host`(host: String, port: Option[Int] = None)
