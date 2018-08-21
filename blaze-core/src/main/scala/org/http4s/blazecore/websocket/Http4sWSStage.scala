package org.http4s
package blazecore
package websocket

import cats.effect._
import cats.implicits._
import fs2._
import fs2.async.mutable.Signal
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.{websocket => ws4s}
import org.http4s.blaze.pipeline.{Command, LeafBuilder, TailStage, TrunkBuilder}
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.internal.unsafeRunAsync
import org.http4s.websocket.WebsocketBits._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

private[http4s] class Http4sWSStage[F[_]](ws: ws4s.Websocket[F])(
    implicit F: ConcurrentEffect[F],
    val ec: ExecutionContext)
    extends TailStage[WebSocketFrame] {

  private[this] val sentClose = new AtomicBoolean(false)

  def name: String = "Http4s WebSocket Stage"

  private val deadSignal: Signal[F, Boolean] =
    F.toIO(async.signalOf[F, Boolean](false)).unsafeRunSync()

  //////////////////////// Source and Sink generators ////////////////////////

  def snk: Sink[F, WebSocketFrame] = _.evalMap { frame =>
    F.delay(sentClose.get()).flatMap { closeSent =>
      if (!closeSent) {
        frame match {
          case c: Close =>
            F.delay(sentClose.compareAndSet(false, true)).flatMap {
              //write the close frame, as it was set atomically
              case true =>
                writeFrame(c)
              case false =>
                //Close was set concurrently, so do nothing
                F.unit
            }
          case _ =>
            writeFrame(frame)
        }
      } else {
        //Close frame has been sent. Send no further data
        F.unit
      }
    }
  }

  private[this] def writeFrame(frame: WebSocketFrame): F[Unit] =
    F.async[Unit] { cb =>
      channelWrite(frame).onComplete {
        case Success(res) => cb(Right(res))
        case Failure(t @ Command.EOF) => cb(Left(t))
        case Failure(t) => cb(Left(t))
      }(directec)
    }

  /** The websocket input stream
    *
    * Note: On receiving a close, we MUST send a close back, as stated in section
    * 5.5.1 of the websocket spec: https://tools.ietf.org/html/rfc6455#section-5.5.1
    *
    * @return
    */
  def inputstream: Stream[F, WebSocketFrame] = {
    val t = F.async[WebSocketFrame] { cb =>
      def go(): Unit =
        channelRead().onComplete {
          case Success(ws) =>
            ws match {
              case c: Close =>
                if (sentClose.get()) {
                  //We sent the close signal, thus we can be sure to end the stream here.
                  F.toIO(deadSignal.set(true)).unsafeRunSync()
                  cb(Right(c))
                } else {
                  //Set sendClose atomically, in case we possibly
                  //Attempt to send two close frames
                  if (sentClose.compareAndSet(false, true)) {
                    channelWrite(c).onComplete {
                      case Success(_) =>
                        F.toIO(deadSignal.set(true)).unsafeRunSync()
                        cb(Right(c))
                      case Failure(t) =>
                        cb(Left(t))
                    }
                  } else {
                    //Close already sent, kill the stream
                    F.toIO(deadSignal.set(true)).unsafeRunSync()
                    cb(Right(c))
                  }
                }
              case Ping(d) =>
                channelWrite(Pong(d)).onComplete {
                  case Success(_) => go()
                  case Failure(Command.EOF) => cb(Left(Command.EOF))
                  case Failure(t) => cb(Left(t))
                }(trampoline)
              case Pong(_) => go()
              case f => cb(Right(f))
            }

          case Failure(Command.EOF) => cb(Left(Command.EOF))
          case Failure(e) => cb(Left(e))
        }(trampoline)
      go()
    }
    Stream.repeatEval(t)
  }

  //////////////////////// Startup and Shutdown ////////////////////////

  override protected def stageStartup(): Unit = {
    super.stageStartup()

    // Effect to send a close to the other endpoint
    val sendClose: F[Unit] = F.delay(sendOutboundCommand(Command.Disconnect))

    val wsStream = inputstream
      .to(ws.receive)
      .concurrently(ws.send.to(snk).drain) //We don't need to terminate if the send stream terminates.
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
