package org.http4s.websocket

import org.http4s.Headers

final case class WebSocketContext[F[_]](webSocket: Websocket[F], headers: Headers = Headers.empty)
