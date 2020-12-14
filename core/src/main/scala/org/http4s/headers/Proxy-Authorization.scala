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

import cats.syntax.all._
import cats.parse.Parser1
import org.http4s.util.Writer

/** {{{
  *   The "Proxy-Authorization" header field allows the client to identify
  *   itself (or its user) to a proxy that requires authentication.
  * }}}
  *
  *  From [[https://tools.ietf.org/html/rfc7235#section-4.4 RFC-7235]]
  */
object `Proxy-Authorization`
    extends HeaderKey.Internal[`Proxy-Authorization`]
    with HeaderKey.Singleton {
  //https://tools.ietf.org/html/rfc7235#section-4.2
  private[http4s] val parser: Parser1[`Proxy-Authorization`] = {
    import org.http4s.internal.parsing.Rfc7235.credentials
    credentials.map(`Proxy-Authorization`(_))
  }

  override def parse(s: String): ParseResult[`Proxy-Authorization`] =
    parser.parseAll(s).leftMap(err => ParseFailure("Invalid Authorization", err.toString))

  def apply(basic: BasicCredentials): Authorization =
    Authorization(Credentials.Token(AuthScheme.Basic, basic.token))
}

final case class `Proxy-Authorization`(credentials: Credentials) extends Header.Parsed {
  override def key: `Proxy-Authorization`.type = `Proxy-Authorization`
  override def renderValue(writer: Writer): writer.type = credentials.render(writer)
}
