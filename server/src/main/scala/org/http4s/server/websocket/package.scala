package org.http4s
package server

import org.http4s.websocket.{WSFrame, Websocket}

import scalaz.stream.Process
import scalaz.concurrent.Task
import Process._

package object websocket {
  val websocketKey = AttributeKey.http4s[Websocket]("websocket")

  def WS(source: Process[Task, WSFrame] = halt,
         sink: Sink[Task, WSFrame] = halt,
         status: Task[Response] = Status.NotImplemented("This is a WebSocket route.")): Task[Response] =
    status.map(_.addAttribute(websocketKey, Websocket(source, sink)))
}
