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

import org.http4s.parser.AdditionalRules
import org.typelevel.ci._

object `Sec-WebSocket-Version` {

  @deprecated("Use fromLong", "0.23.24")
  def apply(version: Long): `Sec-WebSocket-Version` = new `Sec-WebSocket-Version`(version)

  def fromLong(version: Long): ParseResult[`Sec-WebSocket-Version`] =
    if (version >= 0)
      ParseResult.success(new `Sec-WebSocket-Version`(version))
    else
      ParseResult.fail(
        "Invalid version value",
        s"Version $version must be greater than or equal to 0",
      )

  def unsafeFromLong(version: Long): `Sec-WebSocket-Version` =
    fromLong(version).fold(throw _, identity)

  def parse(s: String): ParseResult[`Sec-WebSocket-Version`] =
    ParseResult.fromParser(parser, "Invalid Sec-WebSocket-Accept header")(s)

  private[http4s] val parser = AdditionalRules.NonNegativeLong.map(unsafeFromLong)

  implicit val headerInstance: Header[`Sec-WebSocket-Version`, Header.Single] =
    Header.createRendered(
      ci"Sec-WebSocket-Version",
      _.version,
      parse,
    )

}

final case class `Sec-WebSocket-Version` private[headers] (
    version: Long
)
