package org.http4s.websocket

import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket[F[_]](
    @deprecatedName('read, "0.18-M6") send: Stream[F, WebSocketFrame],
    @deprecatedName('write, "0.18-M6") receive: Sink[F, WebSocketFrame]
) {
  def read: Stream[F, WebSocketFrame] = send
  def write: Sink[F, WebSocketFrame] = receive
}
