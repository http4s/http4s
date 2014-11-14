package org.http4s.websocket

import scalaz.concurrent.Task
import scalaz.stream.{Process, Sink}

import org.http4s.websocket.WebsocketBits.WebSocketFrame

private[http4s] case class Websocket(source: Process[Task, WebSocketFrame], sink: Sink[Task, WebSocketFrame])

