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

import cats.ApplicativeThrow
import cats.MonadThrow
import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.kernel.Outcome
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.net._
import org.http4s._
import org.http4s.crypto.Hash
import org.http4s.crypto.HashAlgorithm
import org.http4s.ember.core.Read
import org.http4s.ember.core.Util.timeoutMaybe
import org.http4s.headers._
import org.http4s.syntax.all._
import org.http4s.websocket.AutoPingCombinedPipe
import org.http4s.websocket.AutoPingSeparatePipe
import org.http4s.websocket.FrameTranscoder
import org.http4s.websocket.Rfc6455
import org.http4s.websocket.WebSocketCombinedPipe
import org.http4s.websocket.WebSocketContext
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketSeparatePipe
import org.typelevel.ci._
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

import java.io.IOException
import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration

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

  private[this] val nonClientTranscoder = new FrameTranscoder(isClient = false)

  private def runConnection[F[_]](
      socket: Socket[F],
      ctx: WebSocketContext[F],
      buffer: Array[Byte],
      receiveBufferSize: Int,
      idleTimeout: Duration,
  )(implicit F: Temporal[F]): F[Unit] = {
    val read: Read[F] = timeoutMaybe(socket.read(receiveBufferSize), idleTimeout)
    def writeFrame(frame: WebSocketFrame): F[Unit] =
      frameToBytes(frame).traverse_(c => timeoutMaybe(socket.write(c), idleTimeout))

    val incoming = Stream.chunk(Chunk.array(buffer)) ++ readStream(read)

    // TODO followup: handle close frames from the user?
    SignallingRef[F, Close](Open).flatMap { close =>
      val (stream, onClose, autoPing) = ctx.webSocket match {
        case webSocket @ WebSocketCombinedPipe(receiveSend, onClose) =>
          webSocket.autoPing match {
            case None =>
              val s = incoming
                .through(decodeFrames[F])
                .evalMapFilter(handleIncomingFrame[F](writeFrame, close))
                .through(receiveSend)
                .foreach(writeFrame)
              (s, onClose, None)
            case Some(AutoPingCombinedPipe(every, frame, receiveSendWithoutAutoPing)) =>
              val s = incoming
                .through(decodeFrames[F])
                .evalMapFilter(handleIncomingFrame[F](writeFrame, close))
                .through(receiveSendWithoutAutoPing)
                .foreach(writeFrame)
              (s, onClose, Some(writeFrame(frame).delayBy(every)))
          }

        case webSocket @ WebSocketSeparatePipe(send, receive, onClose) =>
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

          val reader = incoming
            .through(decodeFrames[F])
            .evalMapFilter(handleIncomingFrame[F](writeFrame, close))
            .through(receive)

          webSocket.autoPing match {
            case None =>
              val writer: Stream[F, Nothing] =
                send.foreach(writeFrame) ++ Stream.exec(sendClosingFrame)
              (reader.concurrently(writer), onClose, None)
            case Some(AutoPingSeparatePipe(every, frame, sendWithoutAutoPing)) =>
              val writer: Stream[F, Nothing] =
                sendWithoutAutoPing.foreach(writeFrame) ++ Stream.exec(sendClosingFrame)
              (reader.concurrently(writer), onClose, Some(writeFrame(frame).delayBy(every)))
          }
      }

      val run = stream
        .interruptWhen(close.map(_ == BothClosed))
        .onFinalize(onClose)
        .compile
        .drain

      autoPing match {
        case None => run
        case Some(pings) =>
          pings.foreverM.background.use((_: F[Outcome[F, Throwable, Nothing]]) => run)
      }
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

  private def frameToBytes(frame: WebSocketFrame): Chunk[Chunk[Byte]] =
    Chunk.array(nonClientTranscoder.frameToBuffer(frame)).map { buffer =>
      // TODO followup: improve the buffering here
      val bytes = new Array[Byte](buffer.remaining())
      buffer.get(bytes)
      Chunk.array(bytes)
    }

  private def decodeFrames[F[_]](implicit F: ApplicativeThrow[F]): Pipe[F, Byte, WebSocketFrame] =
    stream => {
      def go(rest: Stream[F, Byte], acc: Array[Byte]): Pull[F, WebSocketFrame, Unit] =
        rest.pull.uncons.flatMap {
          case Some((chunk, next)) =>
            ApplicativeThrow[Pull[F, WebSocketFrame, *]]
              .catchNonFatal { // `bufferToFrame` might throw
                val buffer = acc ++ chunk.toArray[Byte]
                // A single chunk might contain multiple frames
                // but `bufferToFrame` decodes at most one, so we
                // call it repeatedly until all frames in the buffer are decoded.
                val frames = ArrayBuffer.empty[WebSocketFrame]
                var byteBuffer = ByteBuffer.wrap(buffer)
                var frame = nonClientTranscoder.bufferToFrame(byteBuffer)
                while (frame != null) {
                  frames += frame
                  // We need to slice b/c `bufferToFrame` does absolute reads.
                  byteBuffer = byteBuffer.slice()
                  frame = nonClientTranscoder.bufferToFrame(byteBuffer)
                }
                if (frames.nonEmpty) {
                  val remaining = new Array[Byte](byteBuffer.remaining())
                  byteBuffer.get(remaining)
                  (Some(Chunk.array(frames.toArray)), remaining)
                } else {
                  (None, buffer)
                }
              }
              .flatMap {
                case (Some(frames), remaining) =>
                  Pull.output(frames) >> go(next, remaining)
                case (None, remaining) =>
                  go(next, remaining)
              }
          case None =>
            // TODO followup: sometimes the peer closes connection before stream can interrupt itself
            Pull.raiseError(EndOfStreamError())
        }

      go(stream, Array.emptyByteArray).void.stream
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

  private[this] val magic = ByteVector.view(Rfc6455.handshakeMagicBytes)

  private def serverHandshake[F[_]](value: String)(implicit F: MonadThrow[F]): F[ByteVector] = for {
    value <- ByteVector.encodeAscii(value).liftTo[F]
    digest <- Hash[F].digest(HashAlgorithm.SHA1, value ++ magic)
  } yield digest

  private def readStream[F[_]](read: Read[F]): Stream[F, Byte] =
    Stream.eval(read).flatMap {
      case Some(bytes) =>
        Stream.chunk(bytes) ++ readStream(read)
      case None => Stream.empty
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

  final case class EndOfStreamError() extends Exception("Reached End Of Stream")

  object BrokenPipeError {
    def unapply(err: IOException): Boolean = err.getMessage == "Broken pipe"
  }
}
