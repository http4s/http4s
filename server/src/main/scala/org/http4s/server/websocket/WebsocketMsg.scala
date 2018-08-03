package org.http4s.server.websocket

import org.http4s.websocket.WebsocketBits.{Binary, Text, WebSocketFrame}

/** A simplified websocket message ADT
  *
  */
sealed trait WebsocketMsg {
  def toFrame: WebSocketFrame
}

final case class TextMsg(content: String) extends WebsocketMsg {
  def toFrame: WebSocketFrame = Text(content)
}

final case class BinaryMsg(content: Array[Byte]) extends WebsocketMsg {
  def toFrame: WebSocketFrame = Binary(content)
}

private[http4s] object WebsocketMsg {
  sealed trait State
  case object BufferingText extends State
  case object BufferingBinary extends State
  case object Empty extends State
}
