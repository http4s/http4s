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

import org.typelevel.ci._
import org.http4s.internal.parsing.Rfc7230
import java.util.Base64
import cats.parse.Parser
import cats.parse.Parser.charIn
import cats.parse.Rfc5234.{alpha, digit}

final class `Sec-WebSocket-Key`(hashBytes: Array[Byte]) {
  lazy val hashString: String = Base64.getEncoder().encodeToString(hashBytes)
}

object `Sec-WebSocket-Key` {

  // TODO: catch errors here
  def parse(s: String): ParseResult[`Sec-WebSocket-Key`] =
    ParseResult.fromParser(parser, "Invalid Sec-WebSocket-Key header")(s)


  /* `tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
   *  "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA`
   */
  private[this] val tchar: Parser[Char] = charIn("=").orElse(digit).orElse(alpha)

  /* `token = 1*tchar` */
  private[this] val token: Parser[String] = tchar.rep.string

  private[http4s] val parser = token.map(unsafeFromString)

  private def unsafeFromString(hash: String): `Sec-WebSocket-Key` = {
    val bytes = Base64.getDecoder().decode(hash)
    new `Sec-WebSocket-Key`(bytes)
  }

  implicit val headerInstance: Header[`Sec-WebSocket-Key`, Header.Single] =
    Header.create(
      ci"Sec-WebSocket-Key",
      _.hashString,
      parse
    )
}
