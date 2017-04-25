
package com.example.http4s.blaze

import org.http4s._
import org.http4s.dsl._
import org.http4s.server.websocket._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.StreamApp
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.duration._
import fs2.{Pipe, Scheduler, Sink, Strategy, Stream, Task, async, pipe}
import fs2.time.awakeEvery

object BlazeWebSocketExample extends StreamApp {
  implicit val scheduler = Scheduler.fromFixedDaemonPool(2)
  implicit val strategy = Strategy.fromFixedDaemonPool(8, threadName = "worker")

  val route = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case GET -> Root / "ws" =>
      val toClient: Stream[Task, WebSocketFrame] = awakeEvery[Task](1.seconds).map{ d => Text(s"Ping! $d") }
      val fromClient: Sink[Task, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) => ws match {
        case Text(t, _) => Task.delay(println(t))
        case f          => Task.delay(println(s"Unknown type: $f"))
      }}
      WS(toClient, fromClient)

    case GET -> Root / "wsecho" =>
      val queue = async.unboundedQueue[Task, WebSocketFrame]
      val echoReply: Pipe[Task, WebSocketFrame, WebSocketFrame] = pipe.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
        case _ =>            Text("Something new")
      }

      queue.flatMap { q =>
        val d = q.dequeue.through(echoReply)
        val e = q.enqueue
        WS(d, e)
      }
  }

  def stream(args: List[String]) = BlazeBuilder.bindHttp(8080)
    .withWebSockets(true)
    .mountService(route, "/http4s")
    .serve
}

