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

final case class `Sec-WebSocket-Key`(accept: String)

object `Sec-WebSocket-Key` {

  def parse(s: String): ParseResult[`Sec-WebSocket-Key`] =
    ParseResult.fromParser(parser, "Invalid Sec-WebSocket-Accept header")(s)

  // TODO: RFC 6455 demands that this string be a non-empty base64 string
  private[http4s] val parser = Rfc7230.token.map(`Sec-WebSocket-Key`(_))

  implicit val headerInstance: Header[`Sec-WebSocket-Key`, Header.Single] =
    Header.create(
      ci"Sec-WebSocket-Key",
      _.accept,
      parse
    )
}
