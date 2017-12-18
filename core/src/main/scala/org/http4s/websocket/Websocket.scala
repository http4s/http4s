package org.http4s.websocket

import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket[F[_]](
    @deprecatedName('read, "0.18.0-M7") send: Stream[F, WebSocketFrame],
    @deprecatedName('write, "0.18.0-M7") receive: Sink[F, WebSocketFrame]
) {
  def read: Stream[F, WebSocketFrame] = send
  def write: Sink[F, WebSocketFrame] = receive
}
