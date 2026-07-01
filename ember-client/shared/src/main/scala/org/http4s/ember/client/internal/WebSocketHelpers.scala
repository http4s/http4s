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

import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.MonadCancel
import cats.effect.Resource
import cats.syntax.all._
import fs2.Chunk
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

  val supportedWebSocketVersion = 13L

  val supportedWebSocketVersionHeader: `Sec-WebSocket-Version` =
    `Sec-WebSocket-Version`.unsafeFromLong(
      supportedWebSocketVersion
    )
  val upgradeCi: CIString = ci"upgrade"
  val webSocketProtocol: Protocol = Protocol(ci"websocket", None)
  val connectionUpgrade: Connection = Connection(NonEmptyList.of(upgradeCi))
  val upgradeWebSocket: Upgrade = Upgrade(webSocketProtocol)

  /** The outcome of a successful WebSocket opening handshake: the underlying socket, any bytes
    * the HTTP client already read past the 101 response (the start of the WebSocket stream, which
    * must be replayed before reading more), and the subprotocol the server negotiated.
    */
  final case class WebSocketConnection[F[_]](
      socket: Socket[F],
      leftover: Chunk[Byte],
      subprotocol: Option[`Sec-WebSocket-Protocol`],
  )

  def getSocket[F[_]](client: Client[F], request: Request[F])(implicit
      F: MonadCancel[F, Throwable]
  ): Resource[F, Option[WebSocketConnection[F]]] = {
    val webSocketKey = WebSocketKey.webSocketConnection[F]
    client
      .run(request)
      .evalMap { res =>
        for {
          secWebSocketKeyString <- request.headers
            .get[`Sec-WebSocket-Key`]
            .liftTo[F](new RuntimeException("Sec-WebSocket-Key header not found"))
            .map(_.hashString)
          offeredSubprotocols = request.headers
            .get[`Sec-WebSocket-Protocol`]
            .map(_.values.toList.toSet)
            .getOrElse(Set.empty[String])
          isValid <- validateServerHandshake(res, secWebSocketKeyString, offeredSubprotocols)
          _ <- isValid.liftTo[F]
          negotiatedSubprotocol = res.headers.get[`Sec-WebSocket-Protocol`]
        } yield res.attributes.lookup(webSocketKey).map { socket =>
          val leftover =
            res.attributes.lookup(WebSocketKey.webSocketLeftover).getOrElse(Chunk.empty)
          WebSocketConnection(socket, leftover, negotiatedSubprotocol)
        }
      }
  }

  def toWebSocketFrame[F[_]](wsFrame: WSFrame)(implicit F: MonadThrow[F]): F[WebSocketFrame] =
    wsFrame match {
      case WSFrame.Close(code, reason) => F.fromEither(WebSocketFrame.Close(code, reason))
      case WSFrame.Ping(data) => F.pure(WebSocketFrame.Ping(data))
      case WSFrame.Pong(data) => F.pure(WebSocketFrame.Pong(data))
      case WSFrame.Text(data, last) => F.pure(WebSocketFrame.Text(data, last))
      case WSFrame.Binary(data, last) => F.pure(WebSocketFrame.Binary(data, last))
    }

  def toWSFrame(wsf: WebSocketFrame): WSFrame =
    (wsf: @unchecked) match {
      case c: WebSocketFrame.Close => WSFrame.Close(c.closeCode, c.reason)
      case WebSocketFrame.Ping(data) => WSFrame.Ping(data)
      case WebSocketFrame.Pong(data) => WSFrame.Pong(data)
      case WebSocketFrame.Text(data, last) => WSFrame.Text(data, last)
      case WebSocketFrame.Binary(data, last) => WSFrame.Binary(data, last)
    }

  /** Validate the opening handshake response from the server
    * https://datatracker.ietf.org/doc/html/rfc6455#page-6
    */
  def validateServerHandshake[F[_]: MonadThrow](
      response: Response[F],
      secWebSocketKey: String,
      offeredSubprotocols: Set[String],
  ): F[Either[ServerHandshakeError, Unit]] =
    clientHandshake(secWebSocketKey).map { expectedAccept =>
      serverHandshake(response).flatMap { accept =>
        if (accept == expectedAccept) validateNegotiatedSubprotocol(response, offeredSubprotocols)
        else Left(InvalidSecWebSocketAccept)
      }
    }

  /** RFC 6455 §4.1: the client must fail the connection when the server selects
    * a subprotocol that the client did not offer. The server may select at most one.
    */
  private def validateNegotiatedSubprotocol[F[_]](
      response: Response[F],
      offeredSubprotocols: Set[String],
  ): Either[ServerHandshakeError, Unit] =
    response.headers.get[`Sec-WebSocket-Protocol`] match {
      case None => Either.unit
      case Some(`Sec-WebSocket-Protocol`(NonEmptyList(subprotocol, Nil)))
          if offeredSubprotocols.contains(subprotocol) =>
        Either.unit
      case Some(_) => Left(InvalidSubprotocol)
    }

  private[this] val magic = ByteVector.view(Rfc6455.handshakeMagicBytes)

  def clientHandshake[F[_]](
      value: String
  )(implicit F: MonadThrow[F]): F[ByteVector] = for {
    value <- ByteVector.encodeAscii(value).liftTo[F]
    digest <- Hash[F].digest(HashAlgorithm.SHA1, value ++ magic)
  } yield digest

  private def serverHandshake[F[_]](res: Response[F]): Either[ServerHandshakeError, ByteVector] = {
    val status: Either[ServerHandshakeError, Unit] = res.status match {
      case Status.SwitchingProtocols => Either.unit
      case _ => Left(InvalidStatus)
    }

    val connection: Either[ServerHandshakeError, Unit] = res.headers.get[Connection] match {
      case Some(header) if header.hasUpgrade => Either.unit
      case _ => Left(UpgradeRequired)
    }

    val upgrade: Either[ServerHandshakeError, Unit] = res.headers.get[Upgrade] match {
      case Some(header) if header.values.contains_(webSocketProtocol) => Either.unit
      case _ => Left(UpgradeRequired)
    }

    val secWebSocketAcceptKey: Either[ServerHandshakeError, ByteVector] =
      res.headers.get[`Sec-WebSocket-Accept`] match {
        case Some(header) => Right(header.hashedKey)
        case None => Left(SecWebSocketAcceptNotFound)
      }

    status *> connection *> upgrade *> secWebSocketAcceptKey
  }

  sealed abstract class ServerHandshakeError(val status: Status, val message: String)
      extends RuntimeException(message)
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
  case object InvalidSubprotocol
      extends ServerHandshakeError(
        Status.BadRequest,
        "Sec-WebSocket-Protocol does not match a subprotocol offered by the client",
      )
}
