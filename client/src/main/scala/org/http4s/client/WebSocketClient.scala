package org.http4s
package client

import fs2.{Sink, Stream}
import org.http4s.websocket.WebsocketBits.WebSocketFrame

trait WebSocketClient[F[_]] {
  def connect(req: Request[F]): F[WebSocketClient.Socket[F]]
}

object WebSocketClient {
  def apply[F[_]](f: Request[F] => F[Socket[F]]): WebSocketClient[F] =
    new WebSocketClient[F] {
      def connect(req: Request[F]) = f(req)
    }

  final case class Socket[F[_]](
    send: Sink[F, WebSocketFrame],
    receive: Stream[F, WebSocketFrame])
}
