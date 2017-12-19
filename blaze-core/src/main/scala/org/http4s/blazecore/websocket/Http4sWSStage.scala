package org.http4s
package blazecore
package websocket

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.async.mutable.Signal
import org.http4s.{websocket => ws4s}
import org.http4s.blaze.pipeline.{Command, LeafBuilder, TailStage, TrunkBuilder}
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.websocket.WebsocketBits._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class Http4sWSStage[F[_]](ws: ws4s.Websocket[F])(implicit F: Effect[F], val ec: ExecutionContext)
    extends TailStage[WebSocketFrame] {

  def name: String = "Http4s WebSocket Stage"

  private val deadSignal: F[Signal[F, Boolean]] = async.signalOf[F, Boolean](false)

  //////////////////////// Source and Sink generators ////////////////////////

  def snk: Sink[F, WebSocketFrame] = _.evalMap { frame =>
    F.async[Unit] { cb =>
      channelWrite(frame).onComplete {
        case Success(res) => cb(Right(res))
        case Failure(t @ Command.EOF) => cb(Left(t))
        case Failure(t) => cb(Left(t))
      }(directec)
    }
  }

  def inputstream: Stream[F, WebSocketFrame] = {
    val t = F.async[WebSocketFrame] { cb =>
      def go(): Unit =
        channelRead().onComplete {
          case Success(ws) =>
            ws match {
              case Close(_) =>
                for {
                  t <- deadSignal.map(_.set(true))
                } yield {
                  t.runAsync(_ => IO.unit).unsafeRunSync()
                  cb(Left(Command.EOF))
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

    // A latch for shutting down if both streams are closed.
    val count = new java.util.concurrent.atomic.AtomicInteger(2)

    // If both streams are closed set the signal
    val onStreamFinalize: F[Unit] =
      for {
        dec <- F.delay(count.decrementAndGet())
        _ <- deadSignal.map(signal => if (dec == 0) signal.set(true))
      } yield ()

    // Effect to send a close to the other endpoint
    val sendClose: F[Unit] = F.delay(sendOutboundCommand(Command.Disconnect))

    val wsStream = for {
      dead <- deadSignal
      in = inputstream.to(ws.receive).onFinalize(onStreamFinalize)
      out = ws.send.onFinalize(onStreamFinalize).to(snk).drain
      merged <- in.mergeHaltR(out).interruptWhen(dead).onFinalize(sendClose).run
    } yield merged

    async.unsafeRunAsync {
      wsStream.attempt.flatMap {
        case Left(_) => sendClose
        case Right(_) => ().pure[F]
      }
    } {
      case Left(t) =>
        IO(logger.error(t)("Error closing Web Socket"))
      case Right(_) =>
        // Nothing to do here
        IO.unit
    }
  }

  override protected def stageShutdown(): Unit = {
    deadSignal.map(_.set(true)).runAsync(_ => IO.unit).unsafeRunSync()
    super.stageShutdown()
  }
}

object Http4sWSStage {
  def bufferingSegment[F[_]](stage: Http4sWSStage[F]): LeafBuilder[WebSocketFrame] =
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
}
