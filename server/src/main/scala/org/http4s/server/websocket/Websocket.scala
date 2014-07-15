package org.http4s.server.websocket

import scalaz.concurrent.Task
import scalaz.stream.{Process, Sink}

private[http4s] case class Websocket(source: Process[Task, WSFrame], sink: Sink[Task, WSFrame])

