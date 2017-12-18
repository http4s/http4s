package org.http4s.websocket

import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket[F[_]](
    send: Stream[F, WebSocketFrame],
    receive: Sink[F, WebSocketFrame]
)
