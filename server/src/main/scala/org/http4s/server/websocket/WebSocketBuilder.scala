package org.http4s.server.websocket

import cats._
import cats.implicits._
import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import org.http4s.websocket.{WebSocketContext, Websocket}
import org.http4s.{AttributeEntry, Headers, Response, Status}

case class WebSocketBuilder[F[_]](send: Stream[F, WebSocketFrame], receive: Sink[F, WebSocketFrame])(
    implicit F: Monad[F]) {

  private var onNonWebSocketRequest: F[Response[F]] =
    Response[F](Status.NotImplemented).withBody("This is a WebSocket route.")
  private var onHandshakeFailure: F[Response[F]] =
    Response[F](Status.BadRequest).withBody("WebSocket handshake failed.")
  private var handshakeResponseHeaders: Headers = Headers.empty

  def withFallbackResponse(response: F[Response[F]]): WebSocketBuilder[F] = {
    onNonWebSocketRequest = response
    this
  }

  def withHandshakeFailureResponse(response: F[Response[F]]): WebSocketBuilder[F] = {
    onHandshakeFailure = response
    this
  }

  def putHeaders(headers: Headers): WebSocketBuilder[F] = {
    handshakeResponseHeaders = headers
    this
  }

  def toResponse: F[Response[F]] =
    onNonWebSocketRequest.map(
      _.withAttribute(AttributeEntry(
        websocketKey[F],
        WebSocketContext(Websocket(send, receive), handshakeResponseHeaders, onHandshakeFailure))))
}
