package org.http4s.blazecore
package websocket

import fs2.Stream
import fs2.async.mutable.{Queue, Signal}
import cats.effect.IO
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Http4sSpec
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits._
import org.http4s.blaze.pipeline.Command

class Http4sWSStageSpec extends Http4sSpec {
  class TestWebsocketStage(
      outQ: Queue[IO, WebSocketFrame],
      head: WSTestHead,
      closeHook: AtomicBoolean) {

    def sendWSOutbound(w: WebSocketFrame*): Unit =
      Stream
        .emits(w)
        .covary[IO]
        .to(outQ.enqueue)
        .compile
        .drain
        .unsafeRunSync()

    def sendInbound(w: WebSocketFrame*): Unit =
      w.foreach(head.put)

    def pollOutbound(timeoutSeconds: Long = 4L): Option[WebSocketFrame] =
      head.poll(timeoutSeconds)

    def wasCloseHookCalled(): Boolean =
      closeHook.get()
  }

  object TestWebsocketStage {
    def apply(): TestWebsocketStage = {
      val outQ =
        Queue.unbounded[IO, WebSocketFrame].unsafeRunSync()
      val closeHook = new AtomicBoolean(false)
      val ws: Websocket[IO] =
        Websocket(outQ.dequeue, _.drain, IO(closeHook.set(true)))
      val deadSignal = Signal[IO, Boolean](false).unsafeRunSync()
      val head = LeafBuilder(new Http4sWSStage[IO](ws, closeHook, deadSignal)).base(WSTestHead())
      //Start the websocketStage
      head.sendInboundCommand(Command.Connected)
      new TestWebsocketStage(outQ, head, closeHook)
    }
  }

  sequential

  "Http4sWSStage" should {
    "reply with pong immediately after ping" in {
      val socket = TestWebsocketStage()
      socket.sendInbound(Ping())
      socket.pollOutbound(2) must beSome(Pong())
    }

    "not write any more frames after close frame sent" in {
      val socket = TestWebsocketStage()
      socket.sendWSOutbound(Text("hi"), Close(), Text("lol"))
      socket.pollOutbound() must beSome(Text("hi"))
      socket.pollOutbound() must beSome(Close())
      socket.pollOutbound(1) must beNone
    }

    "send a close frame back and call the on close handler upon receiving a close frame" in {
      val socket = TestWebsocketStage()
      socket.sendInbound(Close())
      socket.pollOutbound() must beSome(Close())
      socket.pollOutbound(1) must beNone
      socket.wasCloseHookCalled() must_== true
    }

    "not send two close frames " in {
      val socket = TestWebsocketStage()
      socket.sendWSOutbound(Close())
      socket.sendInbound(Close())
      socket.pollOutbound() must beSome(Close())
      socket.pollOutbound() must beNone
      socket.wasCloseHookCalled() must_== true
    }

    "ignore pong frames" in {
      val socket = TestWebsocketStage()
      socket.sendInbound(Pong())
      socket.pollOutbound() must beNone
    }
  }
}
