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

import org.specs2.mutable.Specification

class WebSocketHandshakeSpec extends Specification {
  "WebSocketHandshake" should {
    "Be able to split multi value header keys" in {
      val totalValue = "keep-alive, Upgrade"
      val values = List("upgrade", "Upgrade", "keep-alive", "Keep-alive")
      values.foldLeft(true) { (b, v) =>
        b && WebSocketHandshake.valueContains(v, totalValue)
      } should_== true
    }

    "Do a round trip" in {
      val client = WebSocketHandshake.clientHandshaker("www.foo.com")
      val valid = WebSocketHandshake.serverHandshake(client.initHeaders)
      valid must beRight

      val Right(headers) = valid
      client.checkResponse(headers) must beRight
    }
  }
}
