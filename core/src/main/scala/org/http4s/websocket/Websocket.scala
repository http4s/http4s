package org.http4s.websocket

import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket[F[_]](
    @deprecatedName('read) send: Stream[F, WebSocketFrame],
    @deprecatedName('write) receive: Sink[F, WebSocketFrame]
) {

  @deprecated("Parameter has been renamed to `send`", "0.18.0-M7")
  def read: Stream[F, WebSocketFrame] = send

  @deprecated("Parameter has been renamed to `receive`", "0.18.0-M7")
  def write: Sink[F, WebSocketFrame] = receive
}
