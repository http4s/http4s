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

import org.http4s.Http4sSuite

@deprecated("Tests a deprecated feature", "0.23.13")
class WebSocketHandshakeSpec extends Http4sSuite {

  test("WebSocketHandshake should Be able to split multi value header keys") {
    val totalValue = "keep-alive, Upgrade"
    val values = List("upgrade", "Upgrade", "keep-alive", "Keep-alive")
    assert(values.forall(v => WebSocketHandshake.valueContains(v, totalValue)))
  }

  test("WebSocketHandshake should Do a round trip") {
    val client = WebSocketHandshake.clientHandshaker("www.foo.com")
    val valid = WebSocketHandshake.serverHandshake(client.initHeaders)
    assert(valid.isRight)

    val Right(headers) = valid: @unchecked
    assert(client.checkResponse(headers).isRight)
  }

}
