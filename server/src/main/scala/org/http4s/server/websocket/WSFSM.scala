package org.http4s.server.websocket

import java.nio.charset.StandardCharsets.UTF_8

import cats.Monad
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.{Sink, Stream}
import org.http4s.{Headers, Response, Status}
import org.http4s.websocket.WebsocketBits._
import org.http4s.server.websocket.WebsocketMsg._

final class WSFSM[F[_]](f: WebsocketMsg => F[WebsocketMsg], alg: FSMAlgebra[F])(
    implicit F: Monad[F]) {

  def handleText(content: String, last: Boolean): F[Unit] =
    if (last) {
      for {
        st <- alg.getState
        _ <- alg.clearState()
        msg <- st match {
          case BufferingBinary =>
            alg.lastBinary(content.getBytes(UTF_8))
          case BufferingText =>
            alg.lastText(content.getBytes(UTF_8))
          case Empty =>
            F.pure[WebsocketMsg](TextMsg(content))
        }
        out <- f(msg)
        _ <- alg.enqueueMsg(out.toFrame)
      } yield ()
    } else alg.fragmentedText(content)

  def handleBinary(content: Array[Byte], last: Boolean): F[Unit] =
    if (last) {
      for {
        st <- alg.getState
        _ <- alg.clearState()
        msg <- st match {
          case BufferingBinary =>
            alg.lastBinary(content)
          case BufferingText =>
            alg.lastText(content)
          case Empty =>
            F.pure[WebsocketMsg](BinaryMsg(content))
        }
        out <- f(msg)
        _ <- alg.enqueueMsg(out.toFrame)
      } yield ()
    } else alg.fragmentedBinary(content)

  def send: Stream[F, WebSocketFrame] = alg.out

  def recv: Sink[F, WebSocketFrame] = _.evalMap {
    case Text(content, last) =>
      handleText(content, last)
    case Binary(content, last) =>
      handleBinary(content, last)
    case Continuation(content, last) =>
      handleBinary(content, last)
    case _ =>
      F.unit //Do not worry about handling other messages
  }

  def toWSResponse(
      headers: Headers = Headers.empty,
      onNonWebSocketRequest: F[Response[F]] = Response[F](Status.NotImplemented)
        .withEntity("This is a WebSocket route.")
        .pure[F],
      onHandshakeFailure: F[Response[F]] = Response[F](Status.BadRequest)
        .withEntity("WebSocket handshake failed.")
        .pure[F]): F[Response[F]] =
    WebSocketBuilder[F].build(alg.out, recv, headers, onNonWebSocketRequest, onHandshakeFailure)

}

object WSFSM {
  def apply[F[_]: Concurrent](f: WebsocketMsg => F[WebsocketMsg]): F[WSFSM[F]] =
    FSMAlgebra[F].map(new WSFSM[F](f, _))
}
