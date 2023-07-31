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

import org.http4s.internal.parsing.Rfc4648
import org.typelevel.ci._
import scodec.bits.ByteVector

import java.util.Base64
import scala.util.Try

final case class `Sec-WebSocket-Key`(hashedKey: ByteVector) {
  lazy val hashString: String = Base64.getEncoder().encodeToString(hashedKey.toArrayUnsafe)
}

object `Sec-WebSocket-Key` {

  def parse(s: String): ParseResult[`Sec-WebSocket-Key`] =
    ParseResult.fromParser(parser, "Invalid Sec-WebSocket-Key header")(s)

  private[http4s] val parser = Rfc4648.Base64.token.mapFilter { t =>
    Try(unsafeFromString(t)).toOption
  }

  private def unsafeFromString(hash: String): `Sec-WebSocket-Key` = {
    val bytes = Base64.getDecoder().decode(hash)
    `Sec-WebSocket-Key`(ByteVector.view(bytes))
  }

  implicit val headerInstance: Header[`Sec-WebSocket-Key`, Header.Single] =
    Header.create(
      ci"Sec-WebSocket-Key",
      _.hashString,
      parse,
    )
}
