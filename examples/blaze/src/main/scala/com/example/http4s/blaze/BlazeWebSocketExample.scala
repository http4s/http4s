package com.example.http4s.blaze

import org.http4s._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.websocket.WebsocketBits._
import org.http4s.dsl._
import org.http4s.server.websocket._

import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz.concurrent.Strategy
import scalaz.stream.async.unboundedQueue
import scalaz.stream.{Process, Sink}
import scalaz.stream.{DefaultScheduler, Exchange}
import scalaz.stream.time.awakeEvery

object BlazeWebSocketExample extends App {

/// code_ref: blaze_websocket_example
  val route = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case req@ GET -> Root / "ws" =>
      val src = awakeEvery(1.seconds)(Strategy.DefaultStrategy, DefaultScheduler).map{ d => Text(s"Ping! $d") }
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay( println(t))
        case f       => Task.delay(println(s"Unknown type: $f"))
      }
      WS(Exchange(src, sink))

    case req@ GET -> Root / "wsecho" =>
      val q = unboundedQueue[WebSocketFrame]
      val src = q.dequeue.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
      }

      WS(Exchange(src, q.enqueue))
  }

  BlazeBuilder.bindHttp(8080)
    .withWebSockets(true)
    .mountService(route, "/http4s")
    .run
    .awaitShutdown()
/// end_code_ref
}
