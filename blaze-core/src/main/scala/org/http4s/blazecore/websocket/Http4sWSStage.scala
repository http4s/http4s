package org.http4s
package blazecore
package websocket

import cats.effect._
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage, TrunkBuilder}
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.internal.unsafeRunAsync
import org.http4s.websocket.{WebSocket, WebSocketFrame}
import org.http4s.websocket.WebSocketFrame._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

private[http4s] class Http4sWSStage[F[_]](
    ws: WebSocket[F],
    sentClose: AtomicBoolean,
    deadSignal: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], val ec: ExecutionContext)
    extends TailStage[WebSocketFrame] {

  def name: String = "Http4s WebSocket Stage"

  //////////////////////// Source and Sink generators ////////////////////////
  def snk: Pipe[F, WebSocketFrame, Unit] = _.evalMap { frame =>
    F.delay(sentClose.get()).flatMap { wasCloseSent =>
      if (!wasCloseSent) {
        frame match {
          case c: Close =>
            F.delay(sentClose.compareAndSet(false, true))
              .flatMap(cond => if (cond) writeFrame(c, directec) else F.unit)
          case _ =>
            writeFrame(frame, directec)
        }
      } else {
        //Close frame has been sent. Send no further data
        F.unit
      }
    }
  }

  private[this] def writeFrame(frame: WebSocketFrame, ec: ExecutionContext): F[Unit] =
    F.async[Unit] { cb =>
      channelWrite(frame).onComplete {
        case Success(res) => cb(Right(res))
        case Failure(t) => cb(Left(t))
      }(ec)
    }

  private[this] def readFrameTrampoline: F[WebSocketFrame] = F.async[WebSocketFrame] { cb =>
    channelRead().onComplete {
      case Success(ws) => cb(Right(ws))
      case Failure(exception) => cb(Left(exception))
    }(trampoline)
  }

  /** Read from our websocket.
    *
    * To stay faithful to the RFC, the following must hold:
    *
    * - If we receive a ping frame, we MUST reply with a pong frame
    * - If we receive a pong frame, we don't need to forward it.
    * - If we receive a close frame, it means either one of two things:
    *   - We sent a close frame prior, meaning we do not need to reply with one. Just end the stream
    *   - We are the first to receive a close frame, so we try to atomically check a boolean flag,
    *     to prevent sending two close frames. Regardless, we set the signal for termination of
    *     the stream afterwards
    *
    * @return A websocket frame, or a possible IO error.
    */
  private[this] def handleRead(): F[WebSocketFrame] = {
    def maybeSendClose(c: Close): F[Unit] =
      F.delay(sentClose.compareAndSet(false, true)).flatMap { cond =>
        if (cond) writeFrame(c, trampoline)
        else F.unit
      } >> deadSignal.set(true)

    readFrameTrampoline.flatMap {
      case c: Close =>
        for {
          s <- F.delay(sentClose.get())
          //If we sent a close signal, we don't need to reply with one
          _ <- if (s) deadSignal.set(true) else maybeSendClose(c)
        } yield c
      case Ping(d) =>
        //Reply to ping frame immediately
        writeFrame(Pong(d), trampoline) >> handleRead()
      case _: Pong =>
        //Don't forward pong frame
        handleRead()
      case rest =>
        F.pure(rest)
    }
  }

  /** The websocket input stream
    *
    * Note: On receiving a close, we MUST send a close back, as stated in section
    * 5.5.1 of the websocket spec: https://tools.ietf.org/html/rfc6455#section-5.5.1
    *
    * @return
    */
  def inputstream: Stream[F, WebSocketFrame] =
    Stream.repeatEval(handleRead())

  //////////////////////// Startup and Shutdown ////////////////////////

  override protected def stageStartup(): Unit = {
    super.stageStartup()

    // Effect to send a close to the other endpoint
    val sendClose: F[Unit] = F.delay(closePipeline(None))

    val wsStream = inputstream
      .through(ws.receive)
      .concurrently(ws.send.through(snk).drain) //We don't need to terminate if the send stream terminates.
      .interruptWhen(deadSignal)
      .onFinalize(ws.onClose.attempt.void) //Doing it this way ensures `sendClose` is sent no matter what
      .onFinalize(sendClose)
      .compile
      .drain

    unsafeRunAsync(wsStream) {
      case Left(t) =>
        IO(logger.error(t)("Error closing Web Socket"))
      case Right(_) =>
        // Nothing to do here
        IO.unit
    }
  }

  override protected def stageShutdown(): Unit = {
    F.toIO(deadSignal.set(true)).unsafeRunSync()
    super.stageShutdown()
  }
}

object Http4sWSStage {
  def bufferingSegment[F[_]](stage: Http4sWSStage[F]): LeafBuilder[WebSocketFrame] =
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
}
