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
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Pipe
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.testing.DispatcherIOFixture
import org.http4s.websocket.WebSocketFrame
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.CloseFrame
import org.java_websocket.framing.Framedata
import org.java_websocket.framing.PingFrame
import org.java_websocket.handshake.ServerHandshake

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

class EmberServerWebSocketSuite extends Http4sSuite with DispatcherIOFixture {

  def service[F[_]](
      serviceFrame: Ref[F, String]
  )(wsBuilder: WebSocketBuilder2[F])(implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello!")
        case GET -> Root / "ws-echo" =>
          val sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] = _.evalMap {
            case WebSocketFrame.Text(text, _) =>
              Async[F].pure(Option(WebSocketFrame.Text(text)))
            case WebSocketFrame.Close(_) =>
              serviceFrame.set("close").as(Option.empty[WebSocketFrame.Text])
            case _ => Async[F].pure(Option.empty[WebSocketFrame.Text])
          }.unNone

          wsBuilder.build(sendReceive)
        case GET -> Root / "ws-close" =>
          val send = Stream(WebSocketFrame.Text("foo"))
          wsBuilder.build(send, _.void)
        case GET -> Root / "ws-filter-false" =>
          F.deferred[Unit].flatMap { deferred =>
            wsBuilder
              .withFilterPingPongs(false)
              .build(
                Stream.eval(deferred.get).as(WebSocketFrame.Close()),
                _.collect {
                  case WebSocketFrame.Ping(data) if data.decodeAscii.exists(_ == "pingu") =>
                    ()
                }.foreach(deferred.complete(_).void),
              )
          }
      }
      .orNotFound
  }

  def serverResource: Resource[IO, (Ref[IO, String], Server)] =
    Resource
      .eval(Ref.of[IO, String](""))
      .flatMap(ref =>
        EmberServerBuilder
          .default[IO]
          .withPort(port"0")
          .withHttpWebSocketApp(service[IO](ref)(_))
          .build
          .tupleLeft(ref)
      )

  private def fixture = (ResourceFunFixture(serverResource), dispatcher).mapN(FunFixture.map2(_, _))

  sealed case class Client(
      waitOpen: Deferred[IO, Option[Throwable]],
      waitClose: Deferred[IO, Option[Throwable]],
      messages: Queue[IO, String],
      pongs: Queue[IO, String],
      remoteClosed: Deferred[IO, Unit],
      closeCode: Deferred[IO, Int],
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

  def createClient(target: URI, dispatcher: Dispatcher[IO]): IO[Client] =
    for {
      waitOpen <- Deferred[IO, Option[Throwable]]
      waitClose <- Deferred[IO, Option[Throwable]]
      queue <- Queue.unbounded[IO, String]
      pongQueue <- Queue.unbounded[IO, String]
      remoteClosed <- Deferred[IO, Unit]
      closeCode <- Deferred[IO, Int]
      client = new WebSocketClient(target) {
        override def onOpen(handshakedata: ServerHandshake): Unit = {
          val fa = waitOpen.complete(None)
          dispatcher.unsafeRunSync(fa)
          ()
        }
        override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
          val fa = waitOpen
            .complete(Some(new Throwable(s"closed: code: $code, reason: $reason")))
            .attempt >> closeCode.complete(code) >> waitClose.complete(None)
          dispatcher.unsafeRunSync(fa)
          ()
        }
        override def onMessage(msg: String): Unit =
          dispatcher.unsafeRunSync(queue.offer(msg))
        override def onError(ex: Exception): Unit = {
          val fa = waitOpen.complete(Some(ex)).attempt >> waitClose.complete(Some(ex)).attempt.void
          dispatcher.unsafeRunSync(fa)
        }
        override def onWebsocketPong(conn: WebSocket, f: Framedata): Unit = {
          val fa = pongQueue
            .offer(new String(f.getPayloadData().array(), StandardCharsets.UTF_8))
          dispatcher.unsafeRunSync(fa)
        }
        override def onClosing(code: Int, reason: String, remote: Boolean): Unit = {
          dispatcher.unsafeRunSync(remoteClosed.complete(()))
          ()
        }
      }
    } yield Client(waitOpen, waitClose, queue, pongQueue, remoteClosed, closeCode, client)

  fixture.test("open and close connection to server") { case ((_, server), dispatcher) =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
        dispatcher,
      )
      _ <- client.connect
      _ <- client.close
    } yield ()
  }

  fixture.test("send and receive a message") { case ((_, server), dispatcher) =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
        dispatcher,
      )
      _ <- client.connect
      _ <- client.send("foo")
      msg <- client.messages.take
      _ <- client.close
    } yield assertEquals(msg, "foo")
  }

  fixture.test("provide CLOSE frame for user handler") { case ((ref, server), dispatcher) =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
        dispatcher,
      )
      _ <- client.connect
      _ <- client.close
      _ <- IO.sleep(100.millis) // wait update the Close
      msg <- ref.get
    } yield assertEquals(msg, "close")
  }

  fixture.test("respond to pings") { case ((_, server), dispatcher) =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
        dispatcher,
      )
      _ <- client.connect
      _ <- client.ping("hello")
      data <- client.pongs.take
      _ <- client.close
    } yield assertEquals(data, "hello")
  }

  fixture.test("initiate close sequence with code=1000 (NORMAL) on stream termination") {
    case ((_, server), dispatcher) =>
      for {
        client <- createClient(
          URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-close"),
          dispatcher,
        )
        _ <- client.connect
        _ <- client.messages.take
        _ <- client.remoteClosed.get
        code <- client.closeCode.get
      } yield assertEquals(code, CloseFrame.NORMAL)
  }

  fixture.test("respects withFilterPingPongs(false)") { case ((_, server), dispatcher) =>
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-filter-false"),
        dispatcher,
      )
      _ <- client.connect
      _ <- client.ping("pingu")
      _ <- client.remoteClosed.get
    } yield ()
  }

  fixture.test("send and receive multiple messages") { case ((_, server), dispatcher) =>
    val n = 10
    val messages = List.tabulate(n)(i => s"${i + 1}")
    for {
      client <- createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
        dispatcher,
      )
      _ <- client.connect
      _ <- messages.traverse_(client.send)
      messagesReceived <- client.messages.take.replicateA(n)
      _ <- client.close
    } yield assertEquals(messagesReceived, messages)
  }

  fixture.test("do not corrupt frames if handle them concurrently") {
    case ((_, server), dispatcher) =>
      val n = 100
      val p = 100
      val messages = List.tabulate(n)(i => s"${i + 1}")
      val pings = List.tabulate(p)(i => s"ping-$i")

      for {
        client <- createClient(
          URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
          dispatcher,
        )
        _ <- client.connect
        _ <- messages.parTraverse_(client.send).start
        _ <- pings.parTraverse_(client.ping).start
        messagesReceived <- client.messages.take.replicateA(n)
        pongsReceived <- client.pongs.take.replicateA(p)
        _ <- client.close
      } yield {
        assertEquals(messagesReceived.sorted, messages.sorted)
        assertEquals(pongsReceived.sorted, pings.sorted)
      }
  }

}
