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
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF

class WebSocketHelpersSuite extends Http4sSuite {

  test("Invalidate websocket response without HTTP Status 101") {
    forAllF { (secWebSocketKey: `Sec-WebSocket-Key`) =>
      val hashString = secWebSocketKey.hashString
      (
        for {
          hashBytes <- clientHandshake[IO](hashString)
          result <- validateServerHandshake(
            Response[IO]()
              .withHeaders(
                Headers(`Sec-WebSocket-Accept`(hashBytes), upgradeWebSocket, connectionUpgrade)
              ),
            hashString,
          )
        } yield result
      ).map(result => assertEquals(result, Left(InvalidStatus)))
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
          hashBytes <- clientHandshake[IO](hashString)
          result <- validateServerHandshake(
            Response[IO](Status.SwitchingProtocols)
              .withHeaders(
                Headers(`Sec-WebSocket-Accept`(hashBytes), upgradeWebSocket)
              ),
            hashString,
          )
        } yield result
      ).map(result => assertEquals(result, Left(UpgradeRequired)))
    }
  }

  test("Invalidate websocket response without Sec-WebSocket-Accept header") {
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

  val genSecWebSocketKeyTuple = (for {
    secWebSocketKey1 <- arbitrary[`Sec-WebSocket-Key`]
    secWebSocketKey2 <- arbitrary[`Sec-WebSocket-Key`]
  } yield (secWebSocketKey1, secWebSocketKey2)).suchThat { case (x1, x2) => x1 != x2 }

  test("Invalidate websocket response with invalid SecWebSocketAccept key") {
    forAllF[IO, (`Sec-WebSocket-Key`, `Sec-WebSocket-Key`), IO[Unit]](genSecWebSocketKeyTuple) {
      case (secWebSocketKey1, secWebSocketKey2) =>
        val hashString1 = secWebSocketKey1.hashString
        val hashString2 = secWebSocketKey2.hashString
        (
          for {
            hashBytes1 <- clientHandshake[IO](hashString1)
            result <- validateServerHandshake(
              Response[IO](Status.SwitchingProtocols)
                .withHeaders(
                  Headers(`Sec-WebSocket-Accept`(hashBytes1), connectionUpgrade, upgradeWebSocket)
                ),
              hashString2,
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
