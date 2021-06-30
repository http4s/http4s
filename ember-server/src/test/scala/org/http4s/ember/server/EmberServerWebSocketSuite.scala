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
import fs2.{Stream, Pipe}
import org.http4s._
import org.http4s.server.Server
import org.http4s.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.websocket.WebSocketFrame
import org.http4s.server.websocket.WebSocketBuilder

import scala.concurrent.duration._
import org.java_websocket.client.WebSocketClient
import java.net.URI
import cats.effect.concurrent.Deferred
import org.java_websocket.handshake.ServerHandshake

class EmberServerWebSocketSuite extends Http4sSuite {

  def service[F[_]](implicit F: Async[F], timer: Timer[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello!")
        case req @ POST -> Root / "echo" =>
          Ok(req.body)
        case GET -> Root / "ws" =>
          val send: Stream[F, WebSocketFrame] =
            Stream.awakeEvery[F](1.seconds).map(_ => WebSocketFrame.Text("text"))
          val receive: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
            case WebSocketFrame.Text(text, _) => Sync[F].delay(println(text))
            case other => Sync[F].delay(println(other))
          }
          WebSocketBuilder[F].build(send, receive)
      }
      .orNotFound
  }

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service[IO])
      .build


  def fixture = ResourceFixture(serverResource)

  case class Client(onOpen: IO[Unit], onClose: IO[Unit], client: WebSocketClient) {
    def connect: IO[Unit] = IO(client.connect())
    def close: IO[Unit] = IO(client.close())
  }

  fixture.test("can open and close connection to server") { server =>
    def createClient: IO[Client] = 
      for {
        waitOpen <- Deferred[IO, Unit]
        waitClose <- Deferred[IO, Unit]
        client = new WebSocketClient(URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws")) {
          override def onOpen(handshakedata: ServerHandshake): Unit = 
            waitOpen.complete(()).unsafeRunSync()
          override def onClose(code: Int, reason: String, remote: Boolean): Unit = 
            waitClose.complete(()).unsafeRunSync()
          override def onMessage(x$1: String): Unit = ()
          override def onError(ex: Exception): Unit = 
            println(ex)
        }
      } yield Client(waitOpen.get, waitClose.get, client)

    for {
      client <- createClient
      _ <- client.connect
      _ <- client.onOpen
      _ <- client.close
      _ <- client.onClose
    } yield ()
  }
}
