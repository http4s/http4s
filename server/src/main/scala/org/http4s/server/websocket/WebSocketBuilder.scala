package org.http4s.server.websocket

import cats._
import cats.implicits._
import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import org.http4s.websocket.{WebSocketContext, Websocket}
import org.http4s.{AttributeEntry, Headers, Response, Status}

case class WebSocketBuilder[F[_]](
    send: Stream[F, WebSocketFrame],
    receive: Sink[F, WebSocketFrame],
    headers: Headers,
    onNonWebSocketRequest: F[Response[F]],
    onHandshakeFailure: F[Response[F]])(implicit F: Monad[F]) {

  def toResponse: F[Response[F]] =
    onNonWebSocketRequest.map(
      _.withAttribute(
        AttributeEntry(
          websocketKey[F],
          WebSocketContext(Websocket(send, receive), headers, onHandshakeFailure))))
}

object WebSocketBuilder {
  def apply[F[_]](
      send: Stream[F, WebSocketFrame],
      receive: Sink[F, WebSocketFrame],
      headers: Headers = Headers.empty)(implicit F: Monad[F]): WebSocketBuilder[F] =
    new WebSocketBuilder(
      send,
      receive,
      headers,
      Response[F](Status.NotImplemented).withBody("This is a WebSocket route."),
      Response[F](Status.BadRequest).withBody("WebSocket handshake failed.")
    )
}
