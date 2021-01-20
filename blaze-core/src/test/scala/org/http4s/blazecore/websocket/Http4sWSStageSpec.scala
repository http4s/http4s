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

package org.http4s.blazecore
package websocket

import fs2.Stream
import fs2.concurrent.SignallingRef
import cats.effect.IO
import cats.syntax.all._
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.testing.specs2.CatsEffect
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Http4sSpec
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.websocket.{WebSocketFrame, WebSocketSeparatePipe}
import org.http4s.websocket.WebSocketFrame._
import org.http4s.blaze.pipeline.Command

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scodec.bits.ByteVector

class Http4sWSStageSpec extends Http4sSpec with CatsEffect {
  implicit def testExecutionContext: ExecutionContext =
    ExecutionContext.global

  class TestWebsocketStage(
      outQ: Queue[IO, WebSocketFrame],
      head: WSTestHead,
      closeHook: AtomicBoolean,
      backendInQ: Queue[IO, WebSocketFrame]) {
    def sendWSOutbound(w: WebSocketFrame*): IO[Unit] =
      Stream
        .emits(w)
        .covary[IO]
        .through(_.evalMap(outQ.offer))
        .compile
        .drain

    def sendInbound(w: WebSocketFrame*): IO[Unit] =
      w.toList.traverse(head.put).void

    def pollOutbound(timeoutSeconds: Long = 4L): IO[Option[WebSocketFrame]] =
      head.poll(timeoutSeconds)

    def pollBackendInbound(timeoutSeconds: Long = 4L): IO[Option[WebSocketFrame]] =
      IO.race(backendInQ.take, IO.sleep(timeoutSeconds.seconds))
        .map(_.fold(Some(_), _ => None))

    def pollBatchOutputbound(batchSize: Int, timeoutSeconds: Long = 4L): IO[List[WebSocketFrame]] =
      head.pollBatch(batchSize, timeoutSeconds)

    val outStream: Stream[IO, WebSocketFrame] =
      head.outStream

    def wasCloseHookCalled(): IO[Boolean] =
      IO(closeHook.get())
  }

  object TestWebsocketStage {
    def apply()(implicit dispatcher: Dispatcher[IO]): IO[TestWebsocketStage] =
      for {
        outQ <- Queue.unbounded[IO, WebSocketFrame]
        backendInQ <- Queue.unbounded[IO, WebSocketFrame]
        closeHook = new AtomicBoolean(false)
        ws = WebSocketSeparatePipe[IO](
          Stream.repeatEval(outQ.take),
          _.evalMap(backendInQ.offer),
          IO(closeHook.set(true)))
        deadSignal <- SignallingRef[IO, Boolean](false)
        wsHead <- WSTestHead()
        http4sWSStage <- Http4sWSStage[IO](ws, closeHook, deadSignal, dispatcher)
        head = LeafBuilder(http4sWSStage).base(wsHead)
        _ <- IO(head.sendInboundCommand(Command.Connected))
      } yield new TestWebsocketStage(outQ, head, closeHook, backendInQ)
  }

  "Http4sWSStage" should {
    withResource(Dispatcher[IO]) { implicit dispatcher =>
      "reply with pong immediately after ping" in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Ping())
        _ <- socket.pollOutbound(2).map(_ must beSome[WebSocketFrame](Pong()))
        _ <- socket.sendInbound(Close())
      } yield ok)

      "not write any more frames after close frame sent" in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendWSOutbound(Text("hi"), Close(), Text("lol"))
        _ <- socket.pollOutbound().map(_ must_=== Some(Text("hi")))
        _ <- socket.pollOutbound().map(_ must_=== Some(Close()))
        _ <- socket.pollOutbound().map(_ must_=== None)
        _ <- socket.sendInbound(Close())
      } yield ok)

      "send a close frame back and call the on close handler upon receiving a close frame" in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Close())
        _ <- socket.pollBatchOutputbound(2, 2).map(_ must_=== List(Close()))
        _ <- socket.wasCloseHookCalled().map(_ must_=== true)
      } yield ok)

      "not send two close frames " in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendWSOutbound(Close())
        _ <- socket.sendInbound(Close())
        _ <- socket.pollBatchOutputbound(2).map(_ must_=== List(Close()))
        _ <- socket.wasCloseHookCalled().map(_ must_=== true)
      } yield ok)

      "ignore pong frames" in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Pong())
        _ <- socket.pollOutbound().map(_ must_=== None)
        _ <- socket.sendInbound(Close())
      } yield ok)

      "send a ping frames to backend" in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Ping())
        _ <- socket.pollBackendInbound().map(_ must_=== Some(Ping()))
        pingWithBytes = Ping(ByteVector(Array[Byte](1, 2, 3)))
        _ <- socket.sendInbound(pingWithBytes)
        _ <- socket.pollBackendInbound().map(_ must_=== Some(pingWithBytes))
        _ <- socket.sendInbound(Close())
      } yield ok)

      "send a pong frames to backend" in (for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Pong())
        _ <- socket.pollBackendInbound().map(_ must_=== Some(Pong()))
        pongWithBytes = Pong(ByteVector(Array[Byte](1, 2, 3)))
        _ <- socket.sendInbound(pongWithBytes)
        _ <- socket.pollBackendInbound().map(_ must_=== Some(pongWithBytes))
        _ <- socket.sendInbound(Close())
      } yield ok)

      "not fail on pending write request" in (for {
        socket <- TestWebsocketStage()
        reasonSent = ByteVector(42)
        in = Stream.eval(socket.sendInbound(Ping())).repeat.take(100)
        out = Stream.eval(socket.sendWSOutbound(Text("."))).repeat.take(200)
        _ <- in.merge(out).compile.drain
        _ <- socket.sendInbound(Close(reasonSent))
        reasonReceived <-
          socket.outStream
            .collectFirst { case Close(reasonReceived) => reasonReceived }
            .compile
            .toList
            .timeout(5.seconds)
        _ = reasonReceived must_== (List(reasonSent))
      } yield ok)
    }
  }
}
