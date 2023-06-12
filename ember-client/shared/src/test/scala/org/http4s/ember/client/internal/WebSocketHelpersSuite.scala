/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client.internal

import cats.effect.IO
import org.http4s._
import org.http4s.ember.client.internal.WebSocketHelpers._
import org.http4s.headers._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.effect.PropF.forAllF

class WebSocketHelpersSuite extends Http4sSuite {

  test("Invalidate websocket response without HTTP Status 101") {
    forAllF { (secWebSocketKey: `Sec-WebSocket-Key`) =>
      val hashString = secWebSocketKey.hashString
      (for {
        hashBytes <- clientHandshake[IO](hashString)
        result <- validateServerHandshake(
          Response[IO]()
            .withHeaders(
              Headers(`Sec-WebSocket-Accept`(hashBytes), upgradeWebSocket, connectionUpgrade)
            ),
          hashString,
        )
      } yield result).map(result => assertEquals(result, Left(InvalidStatus)))
    }
  }

  test("Invalidate websocket response without upgrade websocket header") {
    forAllF { (secWebSocketKey: `Sec-WebSocket-Key`) =>
      val hashString = secWebSocketKey.hashString
      (
        for {
          hashBytes <- clientHandshake[IO](hashString)
          result <- validateServerHandshake(
            Response[IO](Status.SwitchingProtocols)
              .withHeaders(
                Headers(`Sec-WebSocket-Accept`(hashBytes), connectionUpgrade)
              ),
            hashString,
          )
        } yield result
      ).map(result => assertEquals(result, Left(UpgradeRequired)))
    }
  }

  test("Invalidate websocket response without connection upgrade header") {
    forAllF { (secWebSocketKey: `Sec-WebSocket-Key`) =>
      val hashString = secWebSocketKey.hashString
      (
        for {
          result <- validateServerHandshake(
            Response[IO](Status.SwitchingProtocols)
              .withHeaders(
                Headers(connectionUpgrade, upgradeWebSocket)
              ),
            hashString,
          )
        } yield result
      ).map(result => assertEquals(result, Left(SecWebSocketAcceptNotFound)))
    }
  }

  test("Invalidate websocket response with invalid SecWebSocketAccept key") {
    forAllF { (secWebSocketKey: `Sec-WebSocket-Key`) =>
      val hashString = secWebSocketKey.hashString
      (
        for {
          hashBytes <- clientHandshake[IO](hashString)
          result <- validateServerHandshake(
            Response[IO](Status.SwitchingProtocols)
              .withHeaders(
                Headers(`Sec-WebSocket-Accept`(hashBytes), connectionUpgrade, upgradeWebSocket)
              ),
            "invalidHashString",
          )
        } yield result
      ).map(result => assertEquals(result, Left(InvalidSecWebSocketAccept)))
    }
  }

  test("Accept valid server handshake response") {
    forAllF { (secWebSocketKey: `Sec-WebSocket-Key`) =>
      val hashString = secWebSocketKey.hashString
      (
        for {
          hashBytes <- clientHandshake[IO](hashString)
          result <- validateServerHandshake(
            Response[IO](Status.SwitchingProtocols)
              .withHeaders(
                Headers(`Sec-WebSocket-Accept`(hashBytes), connectionUpgrade, upgradeWebSocket)
              ),
            hashString,
          )
        } yield result
      ).map(result => assertEquals(result, Right(())))
    }
  }
}
