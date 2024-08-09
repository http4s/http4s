package org.http4s.server.websocket

import org.http4s.websocket.WebsocketBits
import org.http4s.websocket.WebsocketBits.WebSocketFrame

/** A simplified websocket message trait:
  * Web socket messages are either
  * WSMessage = Text | Binary | Control
  * Control = Ping | Close
  *
  * Idea being that:
  *   - We don't need to expose `Pong()` frames, those should be replied to immediately
  *     by the server. IF this changes later, a Pong() frame can be added
  *   - We do want to expose ping frames, in case of a client-side keepalive implementation
  *   - Userland binary or text messages should know nothing about fragmentation
  */
sealed trait WSMsg {
  def toFrame: WebSocketFrame
}

object WSMsg {
  def fromFrame(ws: WebSocketFrame): Option[WSMsg] = ws match {
    case t: WebsocketBits.Text => Some(Text(t.str))
    case t: WebsocketBits.Binary => Some(Binary(t.data))
    case _: WebsocketBits.Ping => Some(Ping()) //Todo: Maybe forward data?
    case _: WebsocketBits.Close => Some(Close())
    case _ => None
  }
}

sealed trait CtrlMsg extends WSMsg
sealed trait ContentMsg extends WSMsg

case class Text(content: String) extends ContentMsg {
  override def toFrame =
    WebsocketBits.Text(content, true)
}
case class Binary(content: Array[Byte]) extends ContentMsg {
  override def toFrame: WebSocketFrame =
    WebsocketBits.Binary(content)
}

sealed abstract case class Ping() extends CtrlMsg {
  override def toFrame: WebSocketFrame =
    WebsocketBits.Ping()
}

object Ping {
  private[this] val Instance: Ping = new Ping() {}
  def apply(): Ping = Instance
}

sealed abstract case class Close() extends CtrlMsg {
  override def toFrame: WebSocketFrame =
    WebsocketBits.Close()
}

object Close {
  private[this] val Instance: Close = new Close() {}
  def apply(): Close = Instance
}
