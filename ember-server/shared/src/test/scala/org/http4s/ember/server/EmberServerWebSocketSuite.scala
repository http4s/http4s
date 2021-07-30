/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s.Port
import fs2.Pipe
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.testing.DispatcherIOFixture
import org.http4s.websocket.WebSocketFrame

class EmberServerWebSocketSuite
    extends Http4sSuite
    with DispatcherIOFixture
    with EmberServerWebSocketSuitePlatform {

  def service[F[_]](implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello!")
        case GET -> Root / "ws-echo" =>
          val sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] = _.flatMap {
            case WebSocketFrame.Text(text, _) => Stream(WebSocketFrame.Text(text))
            case _ => Stream(WebSocketFrame.Text("unknown"))
          }
          WebSocketBuilder[F].build(sendReceive)
        case GET -> Root / "ws-close" =>
          val send = Stream(WebSocketFrame.Text("foo"))
          WebSocketBuilder[F].build(send, _.void)
      }
      .orNotFound
  }

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service[IO])
      .withPort(Port.fromInt(org.http4s.server.defaults.HttpPort + 2).get)
      .build

  def fixture = (ResourceFixture(serverResource), dispatcher).mapN(FunFixture.map2(_, _))

  fixture.test("open and close connection to server") { case (server, dispatcher) =>
    for {
      client <- createClient(serverURI(server, "ws-echo"), dispatcher)
      _ <- client.connect
      _ <- client.close
    } yield ()
  }

  fixture.test("send and receive a message") { case (server, dispatcher) =>
    for {
      client <- createClient(serverURI(server, "ws-echo"), dispatcher)
      _ <- client.connect
      _ <- client.send("foo")
      msg <- client.messages.take
      _ <- client.close
    } yield assertEquals(msg, "foo")
  }

  fixture.test("respond to pings") { case (server, dispatcher) =>
    for {
      client <- createClient(serverURI(server, "ws-echo"), dispatcher)
      _ <- client.connect
      _ <- client.ping("hello")
      data <- client.pongs.take
      _ <- client.close
    } yield assertEquals(data, "hello")
  }

  fixture.test("initiate close sequence on stream termination") { case (server, dispatcher) =>
    for {
      client <- createClient(serverURI(server, "ws-close"), dispatcher)
      _ <- client.connect
      _ <- client.messages.take
      _ <- client.remoteClosed.get
    } yield ()
  }
}
