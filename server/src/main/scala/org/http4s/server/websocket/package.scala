package org.http4s
package server

import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits.WebSocketFrame

import scalaz.stream.{Exchange, Process, Sink}
import scalaz.concurrent.Task
import Process._

package object websocket {
  val websocketKey = AttributeKey.http4s[Websocket]("websocket")

  def WS(exchange: Exchange[WebSocketFrame, WebSocketFrame],
         status: Task[Response] = Response(Status.NotImplemented).withBody("This is a WebSocket route.")): Task[Response] =
    status.map(_.withAttribute(websocketKey, Websocket(exchange)))
}
