package org.http4s.server.websocket

import cats._
import cats.implicits._
import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import org.http4s.websocket.{WebSocketContext, Websocket}
import org.http4s.{AttributeEntry, Headers, Response, Status}

case class WebSocketBuilder[F[_]]() {

  def build(send: Stream[F, WebSocketFrame],
            receive: Sink[F, WebSocketFrame],
            headers: Headers,
            onNonWebSocketRequest: F[Response[F]],
            onHandshakeFailure: F[Response[F]])(implicit F: Monad[F]): F[Response[F]] =
    onNonWebSocketRequest.map(
      _.withAttribute(
        AttributeEntry(
          websocketKey[F],
          WebSocketContext(Websocket(send, receive), headers, onHandshakeFailure))))
}

object WebSocketBuilder {

  def defaultNonWebSocketResponse[F[_]: Monad]: F[Response[F]] = Response[F](Status.NotImplemented).withBody("This is a WebSocket route.")
  def defaultHandshakeFailureResponse[F[_]: Monad]: F[Response[F]] = Response[F](Status.NotImplemented).withBody("This is a WebSocket route.")

  class Builder[F[_]: Monad]{
    def apply(send: Stream[F, WebSocketFrame],
              receive: Sink[F, WebSocketFrame],
              headers: Headers = Headers.empty,
              onNonWebSocketRequest: F[Response[F]] = defaultNonWebSocketResponse[F],
              onHandshakeFailure: F[Response[F]] = defaultHandshakeFailureResponse[F]
             ): F[Response[F]] = WebSocketBuilder().build(send, receive, headers, onNonWebSocketRequest, onHandshakeFailure)
  }
  def apply[F[_]: Monad]: Builder[F] = new Builder[F]
}
