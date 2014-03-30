package org.http4s

import scalaz.stream.Process
import scalaz.concurrent.Task
import Process._

/**
 * Created by Bryce Anderson on 3/30/14.
 */
package object websocket {

  case class Websocket(source: Process[Task, WSFrame], sink: Sink[Task, WSFrame])

  val websocketKey = AttributeKey.http4s[Websocket]("websocket")


  sealed trait WSFrame
  case class Text(msg: String) extends WSFrame
  case class Binary(msg: Array[Byte]) extends WSFrame


  def WS[E,D](source: Process[Task, WSFrame], sink: Sink[Task, WSFrame]): Task[Response] = {
    val r = Response(
      status = Status.NotImplemented,
      attributes = AttributeMap(AttributeEntry(websocketKey,Websocket(source, sink)))
    )

    Task.now(r)
  }
}
