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
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Http4sSuite
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.websocket.{WebSocketFrame, WebSocketSeparatePipe}
import org.http4s.websocket.WebSocketFrame._
import org.http4s.blaze.pipeline.Command
import org.http4s.testing.DispatcherIOFixture

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scodec.bits.ByteVector

class Http4sWSStageSpec extends Http4sSuite with DispatcherIOFixture {
  implicit val testExecutionContext: ExecutionContext = munitExecutionContext

  class TestWebsocketStage(
      outQ: Queue[IO, WebSocketFrame],
      head: WSTestHead,
      closeHook: AtomicBoolean,
      backendInQ: Queue[IO, WebSocketFrame]) {
    def sendWSOutbound(w: WebSocketFrame*): IO[Unit] =
      Stream
        .emits(w)
        .covary[IO]
        .evalMap(outQ.offer)
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

  dispatcher.test("Http4sWSStage should reply with pong immediately after ping".flaky) {
    implicit d =>
      for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Ping())
        p <- socket.pollOutbound(2).map(_.exists(_ == Pong()))
        _ <- socket.sendInbound(Close())
      } yield assert(p)
  }

  dispatcher.test("Http4sWSStage should not write any more frames after close frame sent") {
    implicit d =>
      for {
        socket <- TestWebsocketStage()
        _ <- socket.sendWSOutbound(Text("hi"), Close(), Text("lol"))
        p1 <- socket.pollOutbound().map(_.contains(Text("hi")))
        p2 <- socket.pollOutbound().map(_.contains(Close()))
        p3 <- socket.pollOutbound().map(_.isEmpty)
        _ <- socket.sendInbound(Close())
      } yield assert(p1 && p2 && p3)
  }

  dispatcher.test(
    "Http4sWSStage should send a close frame back and call the on close handler upon receiving a close frame") {
    implicit d =>
      for {
        socket <- TestWebsocketStage()
        _ <- socket.sendInbound(Close())
        p1 <- socket.pollBatchOutputbound(2, 2).map(_ == List(Close()))
        p2 <- socket.wasCloseHookCalled().map(_ == true)
      } yield assert(p1 && p2)
  }

  dispatcher.test("Http4sWSStage should not send two close frames".flaky) { implicit d =>
    for {
      socket <- TestWebsocketStage()
      _ <- socket.sendWSOutbound(Close())
      _ <- socket.sendInbound(Close())
      p1 <- socket.pollBatchOutputbound(2).map(_ == List(Close()))
      p2 <- socket.wasCloseHookCalled()
    } yield assert(p1 && p2)
  }

  dispatcher.test("Http4sWSStage should ignore pong frames") { implicit d =>
    for {
      socket <- TestWebsocketStage()
      _ <- socket.sendInbound(Pong())
      p <- socket.pollOutbound().map(_.isEmpty)
      _ <- socket.sendInbound(Close())
    } yield assert(p)
  }

  dispatcher.test("Http4sWSStage should send a ping frames to backend") { implicit d =>
    for {
      socket <- TestWebsocketStage()
      _ <- socket.sendInbound(Ping())
      p1 <- socket.pollBackendInbound().map(_.contains(Ping()))
      pingWithBytes = Ping(ByteVector(Array[Byte](1, 2, 3)))
      _ <- socket.sendInbound(pingWithBytes)
      p2 <- socket.pollBackendInbound().map(_.contains(pingWithBytes))
      _ <- socket.sendInbound(Close())
    } yield assert(p1 && p2)
  }

  dispatcher.test("Http4sWSStage should send a pong frames to backend") { implicit d =>
    for {
      socket <- TestWebsocketStage()
      _ <- socket.sendInbound(Pong())
      p1 <- socket.pollBackendInbound().map(_.contains(Pong()))
      pongWithBytes = Pong(ByteVector(Array[Byte](1, 2, 3)))
      _ <- socket.sendInbound(pongWithBytes)
      p2 <- socket.pollBackendInbound().map(_.contains(pongWithBytes))
      _ <- socket.sendInbound(Close())
    } yield assert(p1 && p2)
  }

  dispatcher.test("Http4sWSStage should not fail on pending write request") { implicit d =>
    for {
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
    } yield assert(reasonReceived == List(reasonSent))
  }
}
