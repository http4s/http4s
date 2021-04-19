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

import cats.effect.{Concurrent, Sync}
import cats.syntax.all._
import fs2.io.tcp._
import org.http4s.syntax.all._
import org.http4s.{Header, Headers, Request, Response, Status}
import org.http4s.websocket.WebSocketContext
import org.http4s.headers.{Connection, Upgrade, `Sec-WebSocket-Accept`, `Sec-WebSocket-Key`, `Sec-WebSocket-Version`}
import org.http4s.ember.core.{Encoder}
import org.http4s.ember.core.Util.durationToFinite

import scala.concurrent.duration.Duration
import java.security.MessageDigest
import java.util.Base64
import java.nio.charset.StandardCharsets
import org.http4s.headers.Connection
import cats.data.NonEmptyList

object WebSocketHelpers {

  private[this] val rfc6455Magic =
    "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII)
  private[this] val supportedWebSocketVersion = 13

  private[this] val upgradeCi = "upgrade".ci
  private[this] val webSocketCi = "websocket".ci
  private[this] val connectionUpgrade = Connection(NonEmptyList.of(upgradeCi))
  private[this] val upgradeWebSocket = Upgrade(NonEmptyList.of(webSocketCi))

  def upgrade[F[_]](
      socket: Socket[F],
      req: Request[F],
      resp: Response[F],
      ctx: WebSocketContext[F],
      idleTimeout: Duration,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit])(implicit F: Concurrent[F]): F[Unit] = {
    val wsResponse = extractRequest(req) match {
      case Right(key) => 
        handshake(key).map { accept =>
          val secWebSocketAccept = `Sec-WebSocket-Accept`(accept)
          val headers = ctx.headers ++ Headers.of(connectionUpgrade, upgradeWebSocket, secWebSocketAccept)
          Response[F](Status.SwitchingProtocols)
            .withHeaders(headers)
        }
      case Left(error) => Response[F](error.status).withEntity(error.message).pure[F]
    }

    wsResponse.flatMap { res =>
      ServerHelpers.send(socket)(Some(req), res, idleTimeout, onWriteFailure).void
    } >> F.never
  }

  private def extractRequest[F[_]](req: Request[F]): Either[HandshakeError, String] = {
    val connection = req.headers.get(Connection) match {
      case Some(header) if header.values.contains_(upgradeCi) => Right(())
      case _ => Left(UpgradeRequired())
    }

    val upgrade = req.headers.get(Upgrade) match {
      case Some(header) if header.values.contains_(webSocketCi) => Right(())
      case _ => Left(UpgradeRequired())
    }

    val version = req.headers.get(`Sec-WebSocket-Version`) match {
      case Some(header) if header.version == supportedWebSocketVersion => Right(())
      case Some(header) => Left(UnsupportedVersion(supportedWebSocketVersion, header.version))
      case None => Left(VersionNotFound())
    }

    val key = req.headers.get(`Sec-WebSocket-Key`) match {
      case Some(header) => Right(header.value)
      case None => Left(KeyNotFound())
    }

    (connection, upgrade, version, key).mapN { case (_, _, _, key) => key }
  }

  private def handshake[F[_]](value: String)(implicit F: Sync[F]): F[String] = F.delay {
    val crypt = MessageDigest.getInstance("SHA-1")
    crypt.reset()
    crypt.update(value.getBytes(StandardCharsets.US_ASCII))
    crypt.update(rfc6455Magic)
    val bytes = crypt.digest()
    Base64.getEncoder.encodeToString(bytes)
  }

  sealed abstract class HandshakeError(val status: Status, val message: String)
  final case class VersionNotFound() extends HandshakeError(Status.BadRequest, "Sec-WebSocket-Version header not present.")
  final case class UnsupportedVersion(supported: Int, requested: Int) extends HandshakeError(Status.UpgradeRequired, s"This server only supports WebSocket version $supported.")
  final case class UpgradeRequired() extends HandshakeError(Status.UpgradeRequired, "Upgrade required for WebSocket communication.")
  final case class KeyNotFound() extends HandshakeError(Status.BadRequest, "Sec-WebSocket-Key header not present.")
}
