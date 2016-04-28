package org.http4s.websocket

import java.net.InetSocketAddress
import org.http4s.Headers
import scalaz.concurrent.Task
import scalaz.stream.Exchange

import org.http4s.websocket.WebsocketBits.WebSocketFrame

case class WebSocket(
  headers: Headers,
  exchange: Exchange[WebSocketFrame, WebSocketFrame],
  localAddress: InetSocketAddress,
  remoteAddress: InetSocketAddress
)
