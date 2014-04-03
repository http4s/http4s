package org.http4s

import scalaz.stream.Process
import scalaz.concurrent.Task
import Process._

/**
 * Created by Bryce Anderson on 3/30/14.
 */
package object websocket {
  val websocketKey = AttributeKey.http4s[Websocket]("websocket")

  def WS(source: Process[Task, WSFrame],
         sink: Sink[Task, WSFrame],
         status: Task[Response] = Status.NotImplemented("This is a WebSocket route.")): Task[Response] =
    status.map(_.addAttribute(websocketKey, Websocket(source, sink)))
}
