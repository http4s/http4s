package org.http4s.websocket

import scalaz.stream.Exchange

import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] final case class Websocket(exchange: Exchange[WebSocketFrame, WebSocketFrame])

