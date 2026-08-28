/*
 * Copyright 2014 http4s.org
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

package org.http4s
package server
package websocket

import cats.effect._
import cats.effect.std.Queue
import cats.effect.testkit.TestControl
import fs2.Pipe
import fs2.Stream
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.websocket.WebSocket
import org.http4s.websocket.WebSocketCombinedPipe
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketSeparatePipe
import scodec.bits.ByteVector

import scala.concurrent.duration._

class WebSocketBuilderSuite extends Http4sSuite {

  // this could be your HTTP application taking a WebSocketBuilder2 as an argument
  private def simpleWsApp(wsb: WebSocketBuilder2[IO]) =
    HttpRoutes
      .of[IO] { case GET -> Root / "ws" =>
        wsb.build(Stream.emit(WebSocketFrame.Text("hello world")), _.as(()))
      }
      .orNotFound

  test("return web socket route default message") {
    WebSocketBuilder2[IO].flatMap { wsb =>
      simpleWsApp(wsb)
        .run(Request[IO](GET, uri"/ws"))
        .flatMap(_.as[String])
        .assertEquals("This is a WebSocket route.")
    }
  }

  private def webSocket(build: WebSocketBuilder2[IO] => IO[Response[IO]]): IO[WebSocket[IO]] =
    WebSocketBuilder2[IO].flatMap { wsb =>
      build(wsb).map(_.attributes.lookup(wsb.webSocketKey).map(_.webSocket).get)
    }

  /** The first `take` frames the server sends to the client, given the frames it receives from it. */
  private def outgoing(
      build: WebSocketBuilder2[IO] => IO[Response[IO]],
      incoming: Stream[IO, WebSocketFrame],
      take: Long = Long.MaxValue,
  ): IO[List[WebSocketFrame]] =
    webSocket(build).flatMap {
      case WebSocketCombinedPipe(receiveSend, _) =>
        receiveSend(incoming).take(take).compile.toList
      case WebSocketSeparatePipe(send, receive, _) =>
        send.concurrently(incoming.through(receive)).take(take).compile.toList
    }

  private val heartbeat = 1.second
  private val silence: Stream[IO, WebSocketFrame] = Stream.never[IO]
  private val drain: Pipe[IO, WebSocketFrame, WebSocketFrame] = _.drain

  private def pongsEvery(
      period: FiniteDuration,
      data: ByteVector = ByteVector.empty,
  ): Stream[IO, WebSocketFrame] =
    Stream.awakeDelay[IO](period).as(WebSocketFrame.Pong(data))

  test("keep pinging as long as the client pongs") {
    TestControl.executeEmbed {
      outgoing(
        _.withHeartbeat(heartbeat).build(drain),
        pongsEvery(heartbeat / 2),
        take = 3,
      ).assertEquals(List.fill(3)(WebSocketFrame.Ping()))
    }
  }

  test("ping every 12 seconds by default") {
    TestControl.executeEmbed {
      outgoing(_.withHeartbeat().build(drain), pongsEvery(1.second), take = 1)
        .productR(IO.monotonic)
        .assertEquals(12.seconds)
    }
  }

  test("close the connection when a pong is overdue") {
    TestControl.executeEmbed {
      outgoing(_.withHeartbeat(heartbeat).build(drain), silence)
        .map {
          case List(WebSocketFrame.Ping(_), close: WebSocketFrame.Close) => close.closeCode
          case other => fail(s"expected a ping followed by a close frame, got $other")
        }
        .assertEquals(1011)
    }
  }

  test("only accept a pong echoing the payload of the ping") {
    val ping = WebSocketFrame.Ping(ByteVector("beat".getBytes))
    TestControl.executeEmbed {
      outgoing(
        _.withHeartbeat(heartbeat, ping).build(drain),
        pongsEvery(heartbeat / 2, ByteVector("other".getBytes)),
      ).map(_.lastOption)
        .map {
          case Some(close: WebSocketFrame.Close) => close.closeCode
          case other => fail(s"expected a close frame, got $other")
        }
        .assertEquals(1011)
    }
  }

  test("ping on the send side of a separate pipe") {
    TestControl.executeEmbed {
      outgoing(
        _.withHeartbeat(heartbeat).build(silence, _.drain),
        pongsEvery(heartbeat / 2),
        take = 2,
      ).assertEquals(List.fill(2)(WebSocketFrame.Ping()))
    }
  }

  /** The frames the server sends, with each frame (or only the first, with `firstWriteOnly`)
    * held for `write` to model the socket write.
    */
  private def outgoingSlowly(
      incoming: Stream[IO, WebSocketFrame],
      write: FiniteDuration,
      take: Long = Long.MaxValue,
      firstWriteOnly: Boolean = false,
  ): IO[List[WebSocketFrame]] =
    Ref.of[IO, Boolean](true).flatMap { first =>
      webSocket(_.withHeartbeat(heartbeat).build(drain)).flatMap {
        case WebSocketCombinedPipe(receiveSend, _) =>
          receiveSend(incoming)
            .evalTap { _ =>
              first
                .getAndSet(false)
                .flatMap(wasFirst => IO.whenA(wasFirst || !firstWriteOnly)(IO.sleep(write)))
            }
            .take(take)
            .compile
            .toList
        case ws => IO(fail(s"expected a combined pipe, got $ws"))
      }
    }

  test("a slow socket write does not close a client that answers in time") {
    TestControl.executeEmbed {
      outgoingSlowly(pongsEvery(heartbeat / 2), write = heartbeat * 7 / 10, take = 4)
        .assertEquals(List.fill(4)(WebSocketFrame.Ping()))
    }
  }

  test("a stalled socket write does not postpone the detection of a dead client") {
    TestControl.executeEmbed {
      outgoingSlowly(silence, write = heartbeat * 5)
        .product(IO.monotonic)
        .assertEquals((Nil, 3 * heartbeat))
    }
  }

  test("close a connection whose write path stalls even when the client answers") {
    TestControl.executeEmbed {
      outgoingSlowly(pongsEvery(heartbeat / 2), write = heartbeat * 10, firstWriteOnly = true)
        .product(IO.monotonic)
        .assertEquals((Nil, 4 * heartbeat))
    }
  }

  test("close a connection that cannot deliver a ping within one interval") {
    TestControl.executeEmbed {
      Queue
        .unbounded[IO, WebSocketFrame]
        .flatMap { pongs =>
          webSocket(_.withHeartbeat(heartbeat).build(drain)).flatMap {
            case WebSocketCombinedPipe(receiveSend, _) =>
              receiveSend(Stream.fromQueueUnterminated(pongs))
                .evalTap(_ => IO.sleep(heartbeat * 3 / 2) *> pongs.offer(WebSocketFrame.Pong()))
                .compile
                .toList
            case ws => IO(fail(s"expected a combined pipe, got $ws"))
          }
        }
        .map(_.lastOption)
        .map {
          case Some(close: WebSocketFrame.Close) => close.closeCode
          case other => fail(s"expected a close frame, got $other")
        }
        .assertEquals(1011)
    }
  }

  test("withoutHeartbeat stops pinging") {
    TestControl.executeEmbed {
      outgoing(
        _.withHeartbeat(heartbeat).withoutHeartbeat.build(drain),
        Stream.sleep_[IO](3 * heartbeat),
      ).assertEquals(Nil)
    }
  }

}
