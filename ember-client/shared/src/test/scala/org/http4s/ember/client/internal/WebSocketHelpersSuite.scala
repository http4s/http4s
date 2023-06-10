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

class WebSocketHelpersSuite extends Http4sSuite {
  private[this] val exampleSecWebSocketKey = "dGhlIHNhbXBsZSBub25jZQ=="
  private[this] val wrongExampleSecWebSocketKey = "d3JvbmcgYWNjZXB0IGtleQ=="

  test("Invalidate websocket response with invalid status") {
    {
      for {
        hashBytes <- clientHandshake[IO](exampleSecWebSocketKey)
        result <- validateServerHandshake(
          Response[IO]()
            .withHeaders(
              Headers(`Sec-WebSocket-Accept`(hashBytes), upgradeWebSocket, connectionUpgrade)
            ),
          exampleSecWebSocketKey,
        )
      } yield result
    }.assertEquals(Left(InvalidStatus))
  }

  test("Invalidate websocket response without upgrade websocket header") {
    {
      for {
        hashBytes <- clientHandshake[IO](exampleSecWebSocketKey)
        result <- validateServerHandshake(
          Response[IO](Status.SwitchingProtocols)
            .withHeaders(
              Headers(`Sec-WebSocket-Accept`(hashBytes), connectionUpgrade)
            ),
          exampleSecWebSocketKey,
        )
      } yield result
    }.assertEquals(Left(UpgradeRequired))
  }

  test("Invalidate websocket response without connection upgrade header") {
    {
      for {
        hashBytes <- clientHandshake[IO](exampleSecWebSocketKey)
        result <- validateServerHandshake(
          Response[IO](Status.SwitchingProtocols)
            .withHeaders(
              Headers(`Sec-WebSocket-Accept`(hashBytes), upgradeWebSocket)
            ),
          exampleSecWebSocketKey,
        )
      } yield result
    }.assertEquals(Left(UpgradeRequired))
  }

  test("Invalidate websocket response without SecWebSocketAccept header") {
    validateServerHandshake(
      Response[IO](Status.SwitchingProtocols)
        .withHeaders(
          Headers(connectionUpgrade, upgradeWebSocket)
        ),
      exampleSecWebSocketKey,
    ).assertEquals(Left(SecWebSocketAcceptNotFound))
  }

  test("Invalidate websocket response with invalid SecWebSocketAccept key") {
    {
      for {
        hashBytes <- clientHandshake[IO](wrongExampleSecWebSocketKey)
        result <- validateServerHandshake(
          Response[IO](Status.SwitchingProtocols)
            .withHeaders(
              Headers(`Sec-WebSocket-Accept`(hashBytes), connectionUpgrade, upgradeWebSocket)
            ),
          exampleSecWebSocketKey,
        )
      } yield result
    }.assertEquals(Left(InvalidSecWebSocketAccept))
  }

  test("Valid server handshake response") {
    {
      for {
        hashBytes <- clientHandshake[IO](exampleSecWebSocketKey)
        result <- validateServerHandshake(
          Response[IO](Status.SwitchingProtocols)
            .withHeaders(
              Headers(`Sec-WebSocket-Accept`(hashBytes), connectionUpgrade, upgradeWebSocket)
            ),
          exampleSecWebSocketKey,
        )
      } yield result
    }.assertEquals(Right(()))
  }
}
