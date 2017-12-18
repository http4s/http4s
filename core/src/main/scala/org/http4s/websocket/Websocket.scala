package org.http4s.websocket

import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket[F[_]](
    @deprecatedName('read) send: Stream[F, WebSocketFrame],
    @deprecatedName('write) receive: Sink[F, WebSocketFrame]
) {
  def read: Stream[F, WebSocketFrame] = send
  def write: Sink[F, WebSocketFrame] = receive
}
