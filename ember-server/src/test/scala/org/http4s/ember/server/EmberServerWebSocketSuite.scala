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
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.Framedata
import org.java_websocket.framing.PingFrame
import org.java_websocket.handshake.ServerHandshake

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class EmberServerWebSocketSuite extends Http4sSuite {

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
      .build

  def fixture = ResourceFixture(serverResource)

  sealed case class Client(
      waitOpen: Deferred[IO, Option[Throwable]],
      waitClose: Deferred[IO, Option[Throwable]],
      messages: Queue[IO, String],
      pongs: Queue[IO, String],
      remoteClosed: Deferred[IO, Unit],
      client: WebSocketClient,
  ) {
    def connect: IO[Unit] =
      IO(client.connect()) >> waitOpen.get.flatMap(ex => IO.fromEither(ex.toLeft(())))
    def close: IO[Unit] =
      IO(client.close()) >> waitClose.get.flatMap(ex => IO.fromEither(ex.toLeft(())))
    def send(msg: String): IO[Unit] = IO(client.send(msg))
    def ping(data: String): IO[Unit] = IO {
      val frame = new PingFrame()
      frame.setPayload(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)))
      client.sendFrame(frame)
    }
  }

  def createClient(target: URI): IO[Client] =
    for {
      waitOpen <- Deferred[IO, Option[Throwable]]
      waitClose <- Deferred[IO, Option[Throwable]]
      queue <- Queue.unbounded[IO, String]
      pongQueue <- Queue.unbounded[IO, String]
      remoteClosed <- Deferred[IO, Unit]
      client = new WebSocketClient(target) {
        override def onOpen(handshakedata: ServerHandshake): Unit = {
          val fa = waitOpen.complete(None)
          fa.unsafeRunSync()
        }
        override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
          val fa = waitOpen
            .complete(Some(new Throwable(s"closed: code: $code, reason: $reason")))
            .attempt >> waitClose.complete(None)
          fa.unsafeRunSync()
        }
        override def onMessage(msg: String): Unit =
          queue.enqueue1(msg).unsafeRunSync()
        override def onError(ex: Exception): Unit = {
          val fa = waitOpen.complete(Some(ex)).attempt >> waitClose.complete(Some(ex)).attempt.void
          fa.unsafeRunSync()
        }
        override def onWebsocketPong(conn: WebSocket, f: Framedata): Unit =
          pongQueue
            .enqueue1(new String(f.getPayloadData().array(), StandardCharsets.UTF_8))
            .unsafeRunSync()
        override def onClosing(code: Int, reason: String, remote: Boolean): Unit =
          remoteClosed.complete(()).unsafeRunSync()
      }
    } yield Client(waitOpen, waitClose, queue, pongQueue, remoteClosed, client)

  fixture.test("open and close connection to server") { server =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo")
      )
      _ <- client.connect
      _ <- client.close
    } yield ()
  }

  fixture.test("send and receive a message") { server =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo")
      )
      _ <- client.connect
      _ <- client.send("foo")
      msg <- client.messages.dequeue1
      _ <- client.close
    } yield assertEquals(msg, "foo")
  }

  fixture.test("respond to pings") { server =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo")
      )
      _ <- client.connect
      _ <- client.ping("hello")
      data <- client.pongs.dequeue1
      _ <- client.close
    } yield assertEquals(data, "hello")
  }

  fixture.test("initiate close sequence on stream termination") { server =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-close")
      )
      _ <- client.connect
      _ <- client.messages.dequeue1
      _ <- client.remoteClosed.get
    } yield ()
  }
}
