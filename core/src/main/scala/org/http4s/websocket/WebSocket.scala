package org.http4s.websocket

import fs2._

private[http4s] final case class WebSocket[F[_]](
    @deprecatedName('read) send: Stream[F, WebSocketFrame],
    @deprecatedName('write) receive: Pipe[F, WebSocketFrame, Unit],
    onClose: F[Unit]
) {

  @deprecated("Parameter has been renamed to `send`", "0.18.0-M7")
  def read: Stream[F, WebSocketFrame] = send

  @deprecated("Parameter has been renamed to `receive`", "0.18.0-M7")
  def write: Pipe[F, WebSocketFrame, Unit] = receive
}
