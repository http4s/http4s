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

import java.nio.charset.StandardCharsets

// https://datatracker.ietf.org/doc/html/rfc6455
private[http4s] object Rfc6455 {
  val handshakeMagic: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
  val handshakeMagicBytes: Array[Byte] = handshakeMagic.getBytes(StandardCharsets.US_ASCII)
}
