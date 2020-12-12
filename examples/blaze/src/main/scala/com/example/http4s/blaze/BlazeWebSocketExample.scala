/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.http4s.blaze

import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.concurrent.Queue
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

object BlazeWebSocketExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeWebSocketExampleApp[IO].stream.compile.drain.as(ExitCode.Success)
}

class BlazeWebSocketExampleApp[F[_]](implicit F: ConcurrentEffect[F], timer: Timer[F])
    extends Http4sDsl[F] {
  def routes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "hello" =>
        Ok("Hello world.")

      case GET -> Root / "ws" =>
        val toClient: Stream[F, WebSocketFrame] =
          Stream.awakeEvery[F](1.seconds).map(d => Text(s"Ping! $d"))
        val fromClient: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(t, _) => F.delay(println(t))
          case f => F.delay(println(s"Unknown type: $f"))
        }
        WebSocketBuilder[F].build(toClient, fromClient)

      case GET -> Root / "wsecho" =>
        val echoReply: Pipe[F, WebSocketFrame, WebSocketFrame] =
          _.collect {
            case Text(msg, _) => Text("You sent the server: " + msg)
            case _ => Text("Something new")
          }

        /* Note that this use of a queue is not typical of http4s applications.
         * This creates a single queue to connect the input and output activity
         * on the WebSocket together. The queue is therefore not accessible outside
         * of the scope of this single HTTP request to connect a WebSocket.
         *
         * While this meets the contract of the service to echo traffic back to
         * its source, many applications will want to create the queue object at
         * a higher level and pass it into the "routes" method or the containing
         * class constructor in order to share the queue (or some other concurrency
         * object) across multiple requests, or to scope it to the application itself
         * instead of to a request.
         */
        Queue
          .unbounded[F, WebSocketFrame]
          .flatMap { q =>
            val d = q.dequeue.through(echoReply)
            val e = q.enqueue
            WebSocketBuilder[F].build(d, e)
          }
    }

  def stream: Stream[F, ExitCode] =
    BlazeServerBuilder[F](global)
      .bindHttp(8080)
      .withHttpApp(routes.orNotFound)
      .serve
}

object BlazeWebSocketExampleApp {
  def apply[F[_]: ConcurrentEffect: Timer]: BlazeWebSocketExampleApp[F] =
    new BlazeWebSocketExampleApp[F]
}
