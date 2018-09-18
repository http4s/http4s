package org.http4s.server.websocket

import cats._
import cats.implicits._
import fs2._
import org.http4s.websocket.WebsocketBits
import org.http4s.websocket.{WebSocketContext, Websocket}
import org.http4s.{AttributeEntry, Headers, Response, Status}

case class WebSocketBuilder[F[_]](
    send: Stream[F, WSMsg],
    receive: Sink[F, ContentMsg],
    headers: Headers,
    onNonWebSocketRequest: F[Response[F]],
    onHandshakeFailure: F[Response[F]])
object WebSocketBuilder {

  private[this] def toWsSink[F[_]](s: Sink[F, ContentMsg]): Sink[F, WebsocketBits.WebSocketFrame] =
    s.compose[Stream[F, WebsocketBits.WebSocketFrame]] { z =>
      z.collect {
        case t: WebsocketBits.Text => Text(t.str)
        case t: WebsocketBits.Binary => Binary(t.data)
      }
    }

  class Builder[F[_]: Applicative] {
    def build(
        send: Stream[F, WSMsg],
        receive: Sink[F, WSMsg],
        headers: Headers = Headers.empty,
        onNonWebSocketRequest: F[Response[F]] =
          Response[F](Status.NotImplemented).withEntity("This is a WebSocket route.").pure[F],
        onHandshakeFailure: F[Response[F]] = Response[F](Status.BadRequest)
          .withEntity("WebSocket handshake failed.")
          .pure[F],
        onClose: F[Unit] = Applicative[F].unit): F[Response[F]] =
      WebSocketBuilder(send, receive, headers, onNonWebSocketRequest, onHandshakeFailure).onNonWebSocketRequest
        .map(
          _.withAttribute(
            AttributeEntry(
              websocketKey[F],
              WebSocketContext(
                Websocket(send.map(_.toFrame), toWsSink[F](receive), onClose),
                headers,
                onHandshakeFailure))))
  }
  def apply[F[_]: Applicative]: Builder[F] = new Builder[F]
}
