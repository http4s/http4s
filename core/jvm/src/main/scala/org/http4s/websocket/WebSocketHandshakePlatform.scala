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

package org.http4s.websocket

import java.nio.charset.StandardCharsets._
import java.util.Base64
import java.security.MessageDigest
import cats.effect.Async

private[websocket] trait WebSocketHandshakePlatform { self: WebSocketHandshake.type =>
  private[websocket] def genAcceptKey[F[_]: Async](str: String): F[String] = Async[F].pure {
    val crypt = MessageDigest.getInstance("SHA-1")
    crypt.reset()
    crypt.update(str.getBytes(US_ASCII))
    crypt.update(magicString)
    val bytes = crypt.digest()
    Base64.getEncoder.encodeToString(bytes)
  }
}
