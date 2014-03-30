package org.http4s

import scalaz.stream.Process
import scalaz.concurrent.Task
import Process._
import org.http4s.Header.`Content-Length`

/**
 * Created by Bryce Anderson on 3/30/14.
 */
package object websocket {

  private[http4s] case class Websocket(source: Process[Task, WSFrame], sink: Sink[Task, WSFrame])

  val websocketKey = AttributeKey.http4s[Websocket]("websocket")


  sealed trait WSFrame
  case class Text(msg: String) extends WSFrame
  case class Binary(msg: Array[Byte]) extends WSFrame
//  case class Ping(data: Array[Byte] = Array.empty) extends WSFrame
//  case class Pong(data: Array[Byte] = Array.empty) extends WSFrame


  def WS(source: Process[Task, WSFrame],
         sink: Sink[Task, WSFrame],
         status: Task[Response] = Status.NotImplemented("This is a WebSocket route.")): Task[Response] =
    status.map(_.addAttribute(websocketKey, Websocket(source, sink)))
}
