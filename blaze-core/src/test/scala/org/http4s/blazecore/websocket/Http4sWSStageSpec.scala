/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.blazecore
package websocket

//import fs2.Stream
//import fs2.concurrent.{Queue, SignallingRef}
//import cats.effect.IO
//import cats.implicits._
//import java.util.concurrent.atomic.AtomicBoolean
//
//import org.http4s.Http4sSpec
//import org.http4s.blaze.pipeline.LeafBuilder
//import org.http4s.websocket.{WebSocketFrame, WebSocketSeparatePipe}
//import org.http4s.websocket.WebSocketFrame._
//import org.http4s.blaze.pipeline.Command
//
//import scala.concurrent.ExecutionContext
//import scala.concurrent.duration._
//import scodec.bits.ByteVector
//import cats.effect.testing.specs2.CatsEffect
//
//class Http4sWSStageSpec extends Http4sSpec with CatsEffect {
//  override implicit def testExecutionContext: ExecutionContext =
//    ExecutionContext.global
//
//  class TestWebsocketStage(
//      outQ: Queue[IO, WebSocketFrame],
//      head: WSTestHead,
//      closeHook: AtomicBoolean,
//      backendInQ: Queue[IO, WebSocketFrame]) {
//    def sendWSOutbound(w: WebSocketFrame*): IO[Unit] =
//      Stream
//        .emits(w)
//        .covary[IO]
//        .through(outQ.enqueue)
//        .compile
//        .drain
//
//    def sendInbound(w: WebSocketFrame*): IO[Unit] =
//      w.toList.traverse(head.put).void
//
//    def pollOutbound(timeoutSeconds: Long = 4L): IO[Option[WebSocketFrame]] =
//      head.poll(timeoutSeconds)
//
//    def pollBackendInbound(timeoutSeconds: Long = 4L): IO[Option[WebSocketFrame]] =
//      IO.delay(backendInQ.dequeue1.unsafeRunTimed(timeoutSeconds.seconds))
//
//    def pollBatchOutputbound(batchSize: Int, timeoutSeconds: Long = 4L): IO[List[WebSocketFrame]] =
//      head.pollBatch(batchSize, timeoutSeconds)
//
//    val outStream: Stream[IO, WebSocketFrame] =
//      head.outStream
//
//    def wasCloseHookCalled(): IO[Boolean] =
//      IO(closeHook.get())
//  }
//
//  object TestWebsocketStage {
//    def apply(): IO[TestWebsocketStage] =
//      for {
//        outQ <- Queue.unbounded[IO, WebSocketFrame]
//        backendInQ <- Queue.unbounded[IO, WebSocketFrame]
//        closeHook = new AtomicBoolean(false)
//        ws = WebSocketSeparatePipe[IO](outQ.dequeue, backendInQ.enqueue, IO(closeHook.set(true)))
//        deadSignal <- SignallingRef[IO, Boolean](false)
//        wsHead <- WSTestHead()
//        head = LeafBuilder(new Http4sWSStage[IO](ws, closeHook, deadSignal)).base(wsHead)
//        _ <- IO(head.sendInboundCommand(Command.Connected))
//      } yield new TestWebsocketStage(outQ, head, closeHook, backendInQ)
//  }
//
//  "Http4sWSStage" should {
//    "reply with pong immediately after ping" in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendInbound(Ping())
//      _ <- socket.pollOutbound(2).map(_ must beSome[WebSocketFrame](Pong()))
//      _ <- socket.sendInbound(Close())
//    } yield ok)
//
//    "not write any more frames after close frame sent" in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendWSOutbound(Text("hi"), Close(), Text("lol"))
//      _ <- socket.pollOutbound().map(_ must_=== Some(Text("hi")))
//      _ <- socket.pollOutbound().map(_ must_=== Some(Close()))
//      _ <- socket.pollOutbound().map(_ must_=== None)
//      _ <- socket.sendInbound(Close())
//    } yield ok)
//
//    "send a close frame back and call the on close handler upon receiving a close frame" in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendInbound(Close())
//      _ <- socket.pollBatchOutputbound(2, 2).map(_ must_=== List(Close()))
//      _ <- socket.wasCloseHookCalled().map(_ must_=== true)
//    } yield ok)
//
//    "not send two close frames " in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendWSOutbound(Close())
//      _ <- socket.sendInbound(Close())
//      _ <- socket.pollBatchOutputbound(2).map(_ must_=== List(Close()))
//      _ <- socket.wasCloseHookCalled().map(_ must_=== true)
//    } yield ok)
//
//    "ignore pong frames" in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendInbound(Pong())
//      _ <- socket.pollOutbound().map(_ must_=== None)
//      _ <- socket.sendInbound(Close())
//    } yield ok)
//
//    "send a ping frames to backend" in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendInbound(Ping())
//      _ <- socket.pollBackendInbound().map(_ must_=== Some(Ping()))
//      pingWithBytes = Ping(ByteVector(Array[Byte](1, 2, 3)))
//      _ <- socket.sendInbound(pingWithBytes)
//      _ <- socket.pollBackendInbound().map(_ must_=== Some(pingWithBytes))
//      _ <- socket.sendInbound(Close())
//    } yield ok)
//
//    "send a pong frames to backend" in (for {
//      socket <- TestWebsocketStage()
//      _ <- socket.sendInbound(Pong())
//      _ <- socket.pollBackendInbound().map(_ must_=== Some(Pong()))
//      pongWithBytes = Pong(ByteVector(Array[Byte](1, 2, 3)))
//      _ <- socket.sendInbound(pongWithBytes)
//      _ <- socket.pollBackendInbound().map(_ must_=== Some(pongWithBytes))
//      _ <- socket.sendInbound(Close())
//    } yield ok)
//
//    "not fail on pending write request" in (for {
//      socket <- TestWebsocketStage()
//      reasonSent = ByteVector(42)
//      in = Stream.eval(socket.sendInbound(Ping())).repeat.take(100)
//      out = Stream.eval(socket.sendWSOutbound(Text("."))).repeat.take(200)
//      _ <- in.merge(out).compile.drain
//      _ <- socket.sendInbound(Close(reasonSent))
//      reasonReceived <-
//        socket.outStream
//          .collectFirst { case Close(reasonReceived) => reasonReceived }
//          .compile
//          .toList
//          .timeout(5.seconds)
//      _ = reasonReceived must_== (List(reasonSent))
//    } yield ok)
//  }
//}
