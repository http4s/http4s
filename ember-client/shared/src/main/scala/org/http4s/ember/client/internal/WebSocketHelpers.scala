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

import cats.Applicative
import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.effect.MonadCancel
import cats.effect.Resource
import cats.syntax.all._
import fs2.io.net.Socket
import org.http4s.Request
import org.http4s.Status
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.websocket.WSFrame
import org.http4s.crypto.Hash
import org.http4s.crypto.HashAlgorithm
import org.http4s.headers._
import org.http4s.websocket.Rfc6455
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci._
import scodec.bits.ByteVector

private[internal] object WebSocketHelpers {

  private[internal] val supportedWebSocketVersion = 13L

  private[internal] val upgradeCi = ci"upgrade"
  private[internal] val webSocketProtocol = Protocol(ci"websocket", None)
  private[internal] val connectionUpgrade = Connection(NonEmptyList.of(upgradeCi))
  private[internal] val upgradeWebSocket = Upgrade(webSocketProtocol)
  private[internal] val exampleSecWebSocketKey = "dGhlIHNhbXBsZSBub25jZQ=="

  def getSocket[F[_]](client: Client[F], request: Request[F])(implicit
      F: MonadCancel[F, Throwable]
  ): Resource[F, Option[Socket[F]]] = {
    val webSocketKey = WebSocketKey.webSocketConnection[F]
    client
      .run(request)
      .evalMap { res =>
        validateServerHandshake(res, exampleSecWebSocketKey)
          .map(isValid => isValid.toOption *> res.attributes.lookup(webSocketKey))
      }
  }

  def toWebSocketFrame[F[_]: Concurrent](wsFrame: WSFrame): F[WebSocketFrame] =
    wsFrame match {
      case WSFrame.Close(code, reason) =>
        MonadThrow[F].fromEither(WebSocketFrame.Close(code, reason))
      case WSFrame.Ping(data) => Applicative[F].pure(WebSocketFrame.Ping(data))
      case WSFrame.Pong(data) => Applicative[F].pure(WebSocketFrame.Pong(data))
      case WSFrame.Text(data, last) => Applicative[F].pure(WebSocketFrame.Text(data, last))
      case WSFrame.Binary(data, last) => Applicative[F].pure(WebSocketFrame.Binary(data, last))
    }

  def toWSFrame(wsf: WebSocketFrame): WSFrame =
    wsf match {
      case c: WebSocketFrame.Close => WSFrame.Close(c.closeCode, c.reason)
      case WebSocketFrame.Ping(data) => WSFrame.Ping(data)
      case WebSocketFrame.Pong(data) => WSFrame.Pong(data)
      case WebSocketFrame.Text(data, last) => WSFrame.Text(data, last)
      case WebSocketFrame.Binary(data, last) => WSFrame.Binary(data, last)
    }

  /** Validate the opening handshake response from the server
    * https://datatracker.ietf.org/doc/html/rfc6455#page-6
    */
  def validateServerHandshake[F[_]](
      response: Response[F],
      secWebSocketKey: String,
  )(implicit F: MonadThrow[F]): F[Either[ServerHandshakeError, Unit]] =
    for {
      secWebSocketAccept <- serverHandshake(response).pure[F]
      correctSecWebSocketAccept <- clientHandshake(secWebSocketKey)
      validated = secWebSocketAccept.flatMap(s =>
        if (s == correctSecWebSocketAccept) Either.unit else Left(InvalidSecWebSocketAccept)
      )
    } yield validated

  private[this] val magic = ByteVector.view(Rfc6455.handshakeMagicBytes)

  def clientHandshake[F[_]](
      value: String
  )(implicit F: MonadThrow[F]): F[ByteVector] = for {
    value <- ByteVector.encodeAscii(value).liftTo[F]
    digest <- Hash[F].digest(HashAlgorithm.SHA1, value ++ magic)
  } yield digest

  private def serverHandshake[F[_]](res: Response[F]): Either[ServerHandshakeError, ByteVector] = {
    val status = res.status match {
      case Status.SwitchingProtocols => Either.unit
      case _ => Left(InvalidStatus)
    }

    val connection = res.headers.get[Connection] match {
      case Some(header) if header.hasUpgrade => Either.unit
      case _ => Left(UpgradeRequired)
    }

    val upgrade = res.headers.get[Upgrade] match {
      case Some(header) if header.values.contains_(webSocketProtocol) => Either.unit
      case _ => Left(UpgradeRequired)
    }

    val secWebSocketAcceptKey = res.headers.get[`Sec-WebSocket-Accept`] match {
      case Some(header) => Right(header.hashedKey)
      case None => Left(SecWebSocketAcceptNotFound)
    }

    (status, connection, upgrade, secWebSocketAcceptKey).mapN {
      case (_, _, _, secWebSocketAcceptKey) =>
        secWebSocketAcceptKey
    }
  }

  sealed abstract class ServerHandshakeError(val status: Status, val message: String)
  case object InvalidStatus
      extends ServerHandshakeError(
        Status.BadRequest,
        "Not found HTTP Status 101 Switching Protocol.",
      )
  case object UpgradeRequired
      extends ServerHandshakeError(
        Status.UpgradeRequired,
        "Upgrade required for WebSocket communication.",
      )
  case object SecWebSocketAcceptNotFound
      extends ServerHandshakeError(Status.BadRequest, "Sec-WebSocket-Accept header not present.")
  case object InvalidSecWebSocketAccept
      extends ServerHandshakeError(
        Status.BadRequest,
        "Sec-WebSocket-Accept does not correspond to the Sec-WebSocket-Key",
      )
}
