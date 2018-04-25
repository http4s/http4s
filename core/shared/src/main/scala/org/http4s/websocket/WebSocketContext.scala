package org.http4s.websocket

import org.http4s.{Headers, Response}

final case class WebSocketContext[F[_]](
    webSocket: Websocket[F],
    headers: Headers,
    failureResponse: F[Response[F]])
