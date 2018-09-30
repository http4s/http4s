package org.http4s
package server

import org.http4s.websocket.WebSocketContext

package object websocket {
  private[this] object Keys {
    val WebSocket: AttributeKey[Any] = AttributeKey[Any]
  }

  def websocketKey[F[_]]: AttributeKey[WebSocketContext[F]] =
    Keys.WebSocket.asInstanceOf[AttributeKey[WebSocketContext[F]]]
}
