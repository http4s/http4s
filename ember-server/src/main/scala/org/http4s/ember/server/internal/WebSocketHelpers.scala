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

package org.http4s.ember.server.internal

import cats.effect.Sync
import cats.syntax.all._
import fs2.io.tcp._
import org.http4s.syntax.all._
import org.http4s.{Header, Request, Response, Status}
import org.http4s.websocket.WebSocketContext
import org.http4s.headers.{Connection, Upgrade, `Sec-WebSocket-Accept`, `Sec-WebSocket-Key`}
import org.http4s.ember.core.{Encoder}
import org.http4s.ember.core.Util.durationToFinite

import scala.concurrent.duration.Duration
import java.security.MessageDigest
import java.util.Base64
import java.nio.charset.StandardCharsets
import org.http4s.headers.Connection
import cats.data.NonEmptyList

object WebSocketHelpers {

  private val rfc6455Magic =
    "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII)

  private val connectionUpgrade = Connection(NonEmptyList.of("upgrade".ci))
  private val upgradeWebSocket = Upgrade(NonEmptyList.of("websocket".ci))

  def upgrade[F[_]: Sync](
      socket: Socket[F],
      req: Request[F],
      resp: Response[F],
      ctx: WebSocketContext[F],
      idleTimeout: Duration,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit]): F[Unit] = {
    // TODO: Craft a 101 Switching Protocols response here
    // Validate that the request needs to be upgrade?

    // TODO: Check upgrade, headers, version

    val wsResponse = req.headers.get(`Sec-WebSocket-Key`) match {
      case Some(key) =>
        handshake(key.value).map { accept =>
          val secWebSocketAccept = `Sec-WebSocket-Accept`(accept)
          Response[F](Status.SwitchingProtocols)
            .withHeaders(secWebSocketAccept, connectionUpgrade, upgradeWebSocket)
        }
      case None =>
        Response[F](Status.BadRequest).withEntity("Missing Sec-WebSocket-Key.").pure[F]
    }

    wsResponse.flatMap { res =>
      ServerHelpers.send(socket)(Some(req), res, idleTimeout, onWriteFailure).void
    }
  }

  private def handshake[F[_]](value: String)(implicit F: Sync[F]): F[String] = F.delay {
    val crypt = MessageDigest.getInstance("SHA-1")
    crypt.reset()
    crypt.update(value.getBytes(StandardCharsets.US_ASCII))
    crypt.update(rfc6455Magic)
    val bytes = crypt.digest()
    Base64.getEncoder.encodeToString(bytes)
  }

}
