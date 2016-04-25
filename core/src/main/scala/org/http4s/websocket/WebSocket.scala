package org.http4s.websocket

import scalaz.concurrent.Task
import scalaz.stream.Exchange

import org.http4s.websocket.WebsocketBits.WebSocketFrame

case class WebSocket(
  exchange: Exchange[WebSocketFrame, WebSocketFrame]
)
