package org.http4s
package server

import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits.WebSocketFrame

import scalaz.stream.{Process, Sink}
import scalaz.concurrent.Task
import Process._

package object websocket {
  val websocketKey = AttributeKey.http4s[Websocket]("websocket")

  def WS(source: Process[Task, WebSocketFrame] = halt,
         sink: Sink[Task, WebSocketFrame] = halt,
         status: Task[Response] = ResponseBuilder(Status.NotImplemented, "This is a WebSocket route.")): Task[Response] =
    status.map(_.withAttribute(websocketKey, Websocket(source, sink)))
}
