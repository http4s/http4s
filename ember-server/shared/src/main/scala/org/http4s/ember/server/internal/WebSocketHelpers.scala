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

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.Temporal
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.net._
import org.http4s._
import org.http4s.ember.core.Read
import org.http4s.ember.core.Util.timeoutMaybe
import org.http4s.headers.Connection
import org.http4s.headers._
import org.http4s.syntax.all._
import org.http4s.websocket.WebSocketCombinedPipe
import org.http4s.websocket.WebSocketContext
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketSeparatePipe
import org.typelevel.ci._
import org.typelevel.log4cats.Logger

import java.io.IOException
import scala.concurrent.duration.Duration

import ember.core.WebSocketHelpers._

private[internal] object WebSocketHelpers {

  private[this] val supportedWebSocketVersion = 13L

  private[this] val upgradeCi = ci"upgrade"
  private[this] val webSocketProtocol = Protocol(ci"websocket", None)
  private[this] val connectionUpgrade = Connection(NonEmptyList.of(upgradeCi))
  private[this] val upgradeWebSocket = Upgrade(webSocketProtocol)

  // TODO followup: use websocketcontext responses for error modes
  def upgrade[F[_]](
      socket: Socket[F],
      req: Request[F],
      ctx: WebSocketContext[F],
      buffer: Array[Byte],
      receiveBufferSize: Int,
      idleTimeout: Duration,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      errorHandler: Throwable => F[Response[F]],
      logger: Logger[F],
  )(implicit F: Temporal[F]): F[Unit] = {
    val wsResponse = clientHandshake(req) match {
      case Right(key) =>
        serverHandshake(key)
          .map { hashBytes =>
            val secWebSocketAccept = new `Sec-WebSocket-Accept`(hashBytes)
            val headers =
              ctx.headers ++ Headers(connectionUpgrade, upgradeWebSocket, secWebSocketAccept)
            Response[F](Status.SwitchingProtocols)
              .withHeaders(headers)
          }
          .handleErrorWith(errorHandler)
      case Left(error) =>
        Response[F](error.status).withEntity(error.message).pure[F]
    }

    val handler = for {
      response <- wsResponse
      _ <- ServerHelpers.send(socket)(Some(req), response, idleTimeout, onWriteFailure)
      _ <-
        if (response.status == Status.SwitchingProtocols)
          runConnection(socket, ctx, buffer, receiveBufferSize, idleTimeout)
        else F.unit
    } yield ()

    handler.handleErrorWith {
      case e @ BrokenPipeError() =>
        logger.trace(e)("WebSocket connection abruptly terminated by client")
      case e @ EndOfStreamError() =>
        logger.trace(e)("WebSocket connection abruptly terminated by client")
      case e => logger.error(e)("WebSocket connection terminated with exception")
    }
  }

  private def runConnection[F[_]](
      socket: Socket[F],
      ctx: WebSocketContext[F],
      buffer: Array[Byte],
      receiveBufferSize: Int,
      idleTimeout: Duration,
  )(implicit F: Temporal[F]): F[Unit] = {
    val read: Read[F] = timeoutMaybe(socket.read(receiveBufferSize), idleTimeout)
    def writeFrame(frame: WebSocketFrame): F[Unit] =
      frameToBytes(frame, false).traverse_(c => timeoutMaybe(socket.write(c), idleTimeout))

    val incoming = Stream.chunk(Chunk.array(buffer)) ++ readStream(read)

    // TODO followup: handle close frames from the user?
    SignallingRef[F, Close](Open).flatMap { close =>
      val (stream, onClose) = ctx.webSocket match {
        case WebSocketCombinedPipe(receiveSend, onClose) =>
          incoming
            .through(decodeFrames[F](false))
            .evalMapFilter(handleIncomingFrame[F](writeFrame, close))
            .through(receiveSend)
            .foreach(writeFrame) -> onClose
        case WebSocketSeparatePipe(send, receive, onClose) =>
          val sendClosingFrame: F[Unit] = close.get.flatMap {
            case Open =>
              for {
                frame <- F.fromEither(WebSocketFrame.Close(1000))
                _ <- close.update {
                  case Open => EndpointClosed
                  case _ => BothClosed
                }
                _ <- writeFrame(frame)
              } yield ()
            case _ => F.unit
          }

          val writer: Stream[F, Nothing] = send.foreach(writeFrame) ++ Stream.exec(sendClosingFrame)

          val reader = incoming
            .through(decodeFrames[F](false))
            .evalMapFilter(handleIncomingFrame[F](writeFrame, close))
            .through(receive)

          reader.concurrently(writer) -> onClose
      }

      stream
        .interruptWhen(close.map(_ == BothClosed))
        .onFinalize(onClose)
        .compile
        .drain
    }
  }

  private def handleIncomingFrame[F[_]](
      writeFrame: WebSocketFrame => F[Unit],
      closeState: Ref[F, Close],
  )(
      frame: WebSocketFrame
  )(implicit F: Concurrent[F]): F[Option[WebSocketFrame]] =
    frame match {
      case ping @ WebSocketFrame.Ping(data) =>
        writeFrame(WebSocketFrame.Pong(data)).as(ping.some)
      case WebSocketFrame.Close(_) =>
        closeState.get.flatMap {
          case Open =>
            for {
              frame <- F.fromEither(WebSocketFrame.Close(1000))
              _ <- writeFrame(frame)
              _ <- closeState.set(BothClosed)
            } yield None
          case _ => F.pure(None)
        }
      case x => F.pure(Some(x))
    }

  private def clientHandshake[F[_]](req: Request[F]): Either[ClientHandshakeError, String] = {
    val connection = req.headers.get[Connection] match {
      case Some(header) if header.hasUpgrade => Either.unit
      case _ => Left(UpgradeRequired)
    }

    val upgrade = req.headers.get[Upgrade] match {
      case Some(header) if header.values.contains_(webSocketProtocol) => Either.unit
      case _ => Left(UpgradeRequired)
    }

    val version = req.headers.get[`Sec-WebSocket-Version`] match {
      case Some(header) if header.version == supportedWebSocketVersion => Either.unit
      case Some(header) => Left(UnsupportedVersion(supportedWebSocketVersion, header.version))
      case None => Left(VersionNotFound)
    }

    val key = req.headers.get[`Sec-WebSocket-Key`] match {
      case Some(header) => Right(header.value)
      case None => Left(KeyNotFound)
    }

    (connection, upgrade, version, key).mapN { case (_, _, _, key) => key }
  }

  sealed abstract class Close
  case object Open extends Close
  case object PeerClosed extends Close
  case object EndpointClosed extends Close
  case object BothClosed extends Close

  sealed abstract class ClientHandshakeError(val status: Status, val message: String)
  case object VersionNotFound
      extends ClientHandshakeError(Status.BadRequest, "Sec-WebSocket-Version header not present.")
  final case class UnsupportedVersion(supported: Long, requested: Long)
      extends ClientHandshakeError(
        Status.UpgradeRequired,
        s"This server only supports WebSocket version $supported.",
      )
  case object UpgradeRequired
      extends ClientHandshakeError(
        Status.UpgradeRequired,
        "Upgrade required for WebSocket communication.",
      )
  case object KeyNotFound
      extends ClientHandshakeError(Status.BadRequest, "Sec-WebSocket-Key header not present.")

  object BrokenPipeError {
    def unapply(err: IOException): Boolean = err.getMessage == "Broken pipe"
  }
}
