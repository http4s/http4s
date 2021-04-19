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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

object `Sec-WebSocket-Version`
    extends HeaderKey.Internal[`Sec-WebSocket-Version`]
    with HeaderKey.Singleton {
  private class VersionImpl(version: Int) extends `Sec-WebSocket-Version`(version)

  def fromInt(version: Int): ParseResult[`Sec-WebSocket-Version`] =
    if (version >= 0)
      ParseResult.success(new VersionImpl(version))
    else
      ParseResult.fail("Invalid version value", s"Version $version must be more or equal to 0")

  def unsafeFromInt(version: Int): `Sec-WebSocket-Version` =
    fromInt(version).fold(throw _, identity)

  override def parse(s: String): ParseResult[`Sec-WebSocket-Version`] =
    HttpHeaderParser.SEC_WEBSOCKET_VERSION(s)
}

sealed abstract case class `Sec-WebSocket-Version`(version: Int) extends Header.Parsed {
  val key = `Sec-WebSocket-Version`

  override val value = version.toString

  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
