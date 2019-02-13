package org.http4s.blazecore
package websocket

import fs2.Stream
import fs2.concurrent.{Queue, SignallingRef}
import cats.effect.IO
import cats.implicits._
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Http4sSpec
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.websocket.{WebSocket, WebSocketFrame}
import org.http4s.websocket.WebSocketFrame._
import org.http4s.blaze.pipeline.Command
import scala.concurrent.ExecutionContext

class Http4sWSStageSpec extends Http4sSpec {
  override implicit def testExecutionContext: ExecutionContext =
    ExecutionContext.global

  class TestWebsocketStage(
      outQ: Queue[IO, WebSocketFrame],
      head: WSTestHead,
      closeHook: AtomicBoolean) {

    def sendWSOutbound(w: WebSocketFrame*): IO[Unit] =
      Stream
        .emits(w)
        .covary[IO]
        .through(outQ.enqueue)
        .compile
        .drain

    def sendInbound(w: WebSocketFrame*): IO[Unit] =
      w.toList.traverse(head.put).void

    def pollOutbound(timeoutSeconds: Long = 4L): IO[Option[WebSocketFrame]] =
      head.poll(timeoutSeconds)

    def pollBatchOutputbound(batchSize: Int, timeoutSeconds: Long = 4L): IO[List[WebSocketFrame]] =
      head.pollBatch(batchSize, timeoutSeconds)

    def wasCloseHookCalled(): IO[Boolean] =
      IO(closeHook.get())
  }

  object TestWebsocketStage {
    def apply(): IO[TestWebsocketStage] =
      for {
        outQ <- Queue.unbounded[IO, WebSocketFrame]
        closeHook = new AtomicBoolean(false)
        ws = WebSocket[IO](outQ.dequeue, _.drain, IO(closeHook.set(true)))
        deadSignal <- SignallingRef[IO, Boolean](false)
        head = LeafBuilder(new Http4sWSStage[IO](ws, closeHook, deadSignal)).base(WSTestHead())
        _ <- IO(head.sendInboundCommand(Command.Connected))
      } yield new TestWebsocketStage(outQ, head, closeHook)
  }

  "Http4sWSStage" should {
    "reply with pong immediately after ping" in (for {
      socket <- TestWebsocketStage()
      _ <- socket.sendInbound(Ping())
      _ <- socket.pollOutbound(2).map(_ must beSome[WebSocketFrame](Pong()))
      _ <- socket.sendInbound(Close())
    } yield ok).unsafeRunSync()

    "not write any more frames after close frame sent" in (for {
      socket <- TestWebsocketStage()
      _ <- socket.sendWSOutbound(Text("hi"), Close(), Text("lol"))
      _ <- socket.pollOutbound().map(_ must_=== Some(Text("hi")))
      _ <- socket.pollOutbound().map(_ must_=== Some(Close()))
      _ <- socket.pollOutbound().map(_ must_=== None)
      _ <- socket.sendInbound(Close())
    } yield ok).unsafeRunSync()

    "send a close frame back and call the on close handler upon receiving a close frame" in (for {
      socket <- TestWebsocketStage()
      _ <- socket.sendInbound(Close())
      _ <- socket.pollBatchOutputbound(2, 2).map(_ must_=== List(Close()))
      _ <- socket.wasCloseHookCalled().map(_ must_=== true)
    } yield ok).unsafeRunSync()

    "not send two close frames " in (for {
      socket <- TestWebsocketStage()
      _ <- socket.sendWSOutbound(Close())
      _ <- socket.sendInbound(Close())
      _ <- socket.pollBatchOutputbound(2).map(_ must_=== List(Close()))
      _ <- socket.wasCloseHookCalled().map(_ must_=== true)
    } yield ok).unsafeRunSync()

    "ignore pong frames" in (for {
      socket <- TestWebsocketStage()
      _ <- socket.sendInbound(Pong())
      _ <- socket.pollOutbound().map(_ must_=== None)
      _ <- socket.sendInbound(Close())
    } yield ok).unsafeRunSync()
  }
}
