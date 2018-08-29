package com.example.http4s.blaze

import cats.effect._
import fs2._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import scala.concurrent.duration._

object BlazeWebSocketExample extends BlazeWebSocketExampleApp

class BlazeWebSocketExampleApp extends IOApp
    with Http4sDsl[IO] {

  def route(implicit timer: Timer[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case GET -> Root / "ws" =>
      val toClient: Stream[IO, WebSocketFrame] =
        Stream.awakeEvery[IO](1.seconds).map(d => Text(s"Ping! $d"))
      val fromClient: Sink[IO, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) =>
        ws match {
          case Text(t, _) => IO(println(t))
          case f => IO(println(s"Unknown type: $f"))
        }
      }
      WebSocketBuilder[IO].build(toClient, fromClient)

    case GET -> Root / "wsecho" =>
      val queue = async.unboundedQueue[IO, WebSocketFrame]
      val echoReply: Pipe[IO, WebSocketFrame, WebSocketFrame] = _.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
        case _ => Text("Something new")
      }

      queue.flatMap { q =>
        val d = q.dequeue.through(echoReply)
        val e = q.enqueue
        WebSocketBuilder[IO].build(d, e)
      }
  }

  def run(args: List[String]): IO[ExitCode] = {
    BlazeBuilder[IO]
      .bindHttp(8080)
      .withWebSockets(true)
      .mountService(route, "/http4s")
      .serve.compile.toList.map(_.head)
  }
}
