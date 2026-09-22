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

  def service[F[_]](wsBuilder: WebSocketBuilder2[F])(implicit F: Async[F]): HttpApp[F] = {
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
          wsBuilder.build(sendReceive)
        case GET -> Root / "ws-close" =>
          val send = Stream(WebSocketFrame.Text("foo"))
          wsBuilder.build(send, _.void)
        case GET -> Root / "ws-close-combined" =>
          wsBuilder.build(_ => Stream(WebSocketFrame.Text("foo")))
        case GET -> Root / "ws-replicated-response" =>
          val send = Stream(WebSocketFrame.Text("42")).repeatN(512)
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

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp(service[IO])
      .build

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

  def createClient(target: URI, dispatcher: Dispatcher[IO]): Resource[IO, Client] = {
    val acquire = for {
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
    Resource.make(acquire)(client => IO(client.client.closeBlocking()))
  }

  fixture.test("open and close connection to server") { case (server, dispatcher) =>
    createClient(
      URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
      dispatcher,
    ).use { client =>
      for {
        _ <- client.connect
        _ <- client.close
      } yield ()
    }
  }

  fixture.test("send and receive a message") { case (server, dispatcher) =>
    createClient(
      URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
      dispatcher,
    ).use { client =>
      for {
        _ <- client.connect
        _ <- client.send("foo")
        msg <- client.messages.take
        _ <- client.close
      } yield assertEquals(msg, "foo")
    }
  }

  fixture.test("respond to pings") { case (server, dispatcher) =>
    createClient(
      URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
      dispatcher,
    ).use { client =>
      for {
        _ <- client.connect
        _ <- client.ping("hello")
        data <- client.pongs.take
        _ <- client.close
      } yield assertEquals(data, "hello")
    }
  }

  fixture.test("initiate close sequence with code=1000 (NORMAL) on stream termination") {
    case (server, dispatcher) =>
      createClient(
        URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-close"),
        dispatcher,
      ).use { client =>
        for {
          _ <- client.connect
          _ <- client.messages.take
          _ <- client.remoteClosed.get
          code <- client.closeCode.get
        } yield assertEquals(code, CloseFrame.NORMAL)
      }
  }

  fixture.test("server response and pong frames do not interfere") { case (server, dispatcher) =>
    createClient(
      URI.create(
        s"ws://${server.address.getHostName}:${server.address.getPort}/ws-replicated-response"
      ),
      dispatcher,
    ).use { client =>
      val onCancel = IO.raiseError(new IllegalStateException("Fiber canceled"))

      for {
        _ <- client.connect
        pings =
          (0 to 511).toList
            .traverse_(i => client.ping(s"ping-$i"))
        takes = client.messages.take.replicateA(512)
        serverResponse <-
          IO.racePair(pings, takes)
            .flatMap {
              case Left((Outcome.Succeeded(_), takeFiber)) =>
                takeFiber.joinWith(onCancel)
              case Left((Outcome.Errored(ex), takeFiber)) =>
                takeFiber.cancel *> IO.raiseError(ex)
              case Left((Outcome.Canceled(), takeFiber)) =>
                takeFiber.cancel *> onCancel
              case Right((pingFiber, Outcome.Succeeded(res))) =>
                pingFiber.cancel *> res
              case Right((pingFiber, Outcome.Errored(ex))) =>
                pingFiber.cancel *> IO.raiseError(ex)
              case Right((pingFiber, Outcome.Canceled())) =>
                pingFiber.cancel *> onCancel
            }
            .timeout(10.seconds)
        code <- client.closeCode.get
      } yield {
        assertEquals(serverResponse, List.fill(512)("42"))
        assertEquals(code, CloseFrame.NORMAL)
      }
    }
  }

  fixture.test(
    "combined pipe: server initiates close sequence with code=1000 (NORMAL) on stream completion"
  ) { case (server, dispatcher) =>
    createClient(
      URI.create(
        s"ws://${server.address.getHostName}:${server.address.getPort}/ws-close-combined"
      ),
      dispatcher,
    ).use { client =>
      for {
        _ <- client.connect
        _ <- client.remoteClosed.get.timeout(10.seconds)
        msg <- client.messages.take
        code <- client.closeCode.get
      } yield {
        assertEquals(msg, "foo")
        assertEquals(code, CloseFrame.NORMAL)
      }
    }
  }

  fixture.test("respects withFilterPingPongs(false)") { case (server, dispatcher) =>
    createClient(
      URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-filter-false"),
      dispatcher,
    ).use { client =>
      for {
        _ <- client.connect
        _ <- client.ping("pingu")
        _ <- client.remoteClosed.get
      } yield ()
    }
  }

  fixture.test("send and receive multiple messages") { case (server, dispatcher) =>
    val n = 10
    val messages = List.tabulate(n)(i => s"${i + 1}")
    createClient(
      URI.create(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"),
      dispatcher,
    ).use { client =>
      for {
        _ <- client.connect
        _ <- messages.traverse_(client.send)
        messagesReceived <- client.messages.take.replicateA(n)
        _ <- client.close
      } yield assertEquals(messagesReceived, messages)
    }
  }

}
