package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object BlazeWebSocketExample extends BlazeWebSocketExampleApp[IO]

class BlazeWebSocketExampleApp[F[_]](implicit F: ConcurrentEffect[F])
    extends StreamApp[F]
    with Http4sDsl[F] {

  def route(implicit timer: Timer[F]): HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case GET -> Root / "ws" =>
      val toClient: Stream[F, WebSocketFrame] =
        Stream.awakeEvery[F](1.seconds).map(d => Text(s"Ping! $d"))
      val fromClient: Sink[F, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) =>
        ws match {
          case Text(t, _) => F.delay(println(t))
          case f => F.delay(println(s"Unknown type: $f"))
        }
      }
      WebSocketBuilder[F].build(toClient, fromClient)

    case GET -> Root / "wsecho" =>
      val queue = async.unboundedQueue[F, WebSocketFrame]
      val echoReply: Pipe[F, WebSocketFrame, WebSocketFrame] = _.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
        case _ => Text("Something new")
      }

      queue.flatMap { q =>
        val d = q.dequeue.through(echoReply)
        val e = q.enqueue
        WebSocketBuilder[F].build(d, e)
      }
  }

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] = {
    implicit val timer: Timer[F] = Timer.derive[F]
    BlazeBuilder[F]
      .bindHttp(8080)
      .withWebSockets(true)
      .mountService(route, "/http4s")
      .serve
  }
}
