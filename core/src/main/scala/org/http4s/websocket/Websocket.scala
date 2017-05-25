package org.http4s.websocket

import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket[F[_]](
  read: Stream[F, WebSocketFrame],
  write: Sink[F, WebSocketFrame]
)

