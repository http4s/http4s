package org.http4s
package client

import fs2.{Sink, Stream}
import java.net.SocketAddress
import org.http4s.websocket.WebsocketBits.WebSocketFrame

trait WebSocketClient[F[_]] {
  def connect(req: Request[F]): F[WebSocket[F]]
}

object WebSocketClient {
  def apply[F[_]](f: Request[F] => F[WebSocket[F]]): WebSocketClient[F] =
    new WebSocketClient[F] {
      def connect(req: Request[F]) = f(req)
    }
}

abstract class WebSocket[F[_]] {
  def read1: F[WebSocketFrame]

  def read: Stream[F, WebSocketFrame] =
    Stream.eval(read1).repeat

  def write1(frame: WebSocketFrame): F[Unit]

  def write: Sink[F, WebSocketFrame] =
    Sink(write1)

  def localAddress: SocketAddress

  def remoteAddress: SocketAddress
}
