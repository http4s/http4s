package org.http4s.blazecore
package websocket

import fs2.Stream
import fs2.async.mutable.Queue
import cats.effect.IO
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Http4sSpec
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits._
import org.http4s.blaze.pipeline.Command

class Http4sWSStageSpec extends Http4sSpec {
  class TestSocket(
      outQ: Queue[IO, WebSocketFrame],
      head: WSTestHead,
      tail: Http4sWSStage[IO],
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

    def pollOutbound(timeoutSeconds: Long = 2L): Option[WebSocketFrame] =
      head.poll(timeoutSeconds)

    def shutDown(): Unit =
      tail.sendOutboundCommand(Command.Disconnect)

    def wasCloseHookCalled(): Boolean =
      closeHook.get()
  }

  object TestSocket {
    def apply(): TestSocket = {
      val outQ =
        Queue.unbounded[IO, WebSocketFrame].unsafeRunSync()
      val closeHook = new AtomicBoolean(false)
      val ws: Websocket[IO] =
        Websocket(outQ.dequeue, _.drain, IO(closeHook.set(true)))
      val wsStage = new Http4sWSStage[IO](ws)
      val head = LeafBuilder(wsStage).base(new WSTestHead)
      //Start the websocket
      head.sendInboundCommand(Command.Connected)
      new TestSocket(outQ, head, wsStage, closeHook)
    }
  }

  sequential

  "Http4sWSStage" should {
    "reply with pong immediately after ping" in {
      val socket = TestSocket()
      socket.sendInbound(Ping())
      socket.shutDown()
      socket.pollOutbound(2) must beSome(Pong())
    }

    "not write any more frames after close frame sent" in {
      val socket = TestSocket()
      socket.sendWSOutbound(Text("hi"), Close(), Text("lol"))
      socket.shutDown()
      socket.pollOutbound() must beSome(Text("hi"))
      socket.pollOutbound() must beSome(Close())
      socket.pollOutbound(1) must beNone
    }

    "send a close frame back and call the on close handler upon receiving a close frame" in {
      val socket = TestSocket()
      socket.sendInbound(Close())
      socket.pollOutbound() must beSome(Close())
      socket.pollOutbound(1) must beNone
      socket.wasCloseHookCalled() must_== true
    }

    "not send two close frames " in {
      val socket = TestSocket()
      socket.sendWSOutbound(Close())
      socket.sendInbound(Close())
      socket.pollOutbound() must beSome(Close())
      socket.pollOutbound(1) must beNone
      socket.wasCloseHookCalled() must_== true
    }
  }

}
