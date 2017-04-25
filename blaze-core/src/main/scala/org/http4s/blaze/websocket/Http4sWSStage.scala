package org.http4s
package blaze
package websocket

import fs2.async.mutable.Signal
import org.http4s.websocket.WebsocketBits._

import scala.util.{Failure, Success}
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.{websocket => ws4s}

import fs2.async
import fs2._

import pipeline.{TrunkBuilder, LeafBuilder, Command, TailStage}

class Http4sWSStage(ws: ws4s.Websocket)(implicit val strategy: Strategy) extends TailStage[WebSocketFrame] {
  def name: String = "Http4s WebSocket Stage"

  // TODO Remove
  def log[A](prefix: String): Pipe[Task, A, A] = _.evalMap{a => Task.delay {println(s"$prefix> $a"); a} }

  private val deadSignal: Task[Signal[Task, Boolean]] = async.signalOf[Task, Boolean](false)

  //////////////////////// Source and Sink generators ////////////////////////

  def snk: Sink[Task, WebSocketFrame] = _.evalMap { frame =>
    Task.async[Unit] { cb =>
      channelWrite(frame).onComplete {
        case Success(res)             => cb(Right(res))
        case Failure(t @ Command.EOF) => cb(Left(t))
        case Failure(t)               => cb(Left(t))
      }(directec)
    }
  }

  def inputstream: Stream[Task, WebSocketFrame] = {
    val t = Task.async[WebSocketFrame] { cb =>
      def go(): Unit = channelRead().onComplete {
        case Success(ws)         => ws match {
            case Close(_)    =>
              for {
                t <- deadSignal.map(_.set(true))
              } yield {
                t.unsafeRun()
                cb(Left(Command.EOF))
              }

            // TODO: do we expect ping frames here?
            case Ping(d)     =>  channelWrite(Pong(d)).onComplete {
              case Success(_)           => go()
              case Failure(Command.EOF) => cb(Left(Command.EOF))
              case Failure(t)           => cb(Left(t))
            }(trampoline)

            case Pong(_)     => go()
            case f           => cb(Right(f))
          }

        case Failure(Command.EOF) => cb(Left(Command.EOF))
        case Failure(e)           => cb(Left(e))
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
    val onStreamFinalize: Task[Unit] =
      for {
        dec <- Task.delay(count.decrementAndGet())
        _   <- deadSignal.map(signal => if (dec == 0) signal.set(true))
      } yield ()

    // Task to send a close to the other endpoint
    val sendClose: Task[Unit] = Task.delay(sendOutboundCommand(Command.Disconnect))

    val wsStream = for {
      dead   <- deadSignal
      in     = inputstream.through(log("input")).to(ws.write).onFinalize(onStreamFinalize)
      out    = ws.read.through(log("output")).onFinalize(onStreamFinalize).to(snk).drain
      merged <- (in mergeHaltR out).interruptWhen(dead).onFinalize(sendClose).run
    } yield merged

    wsStream.or(sendClose).unsafeRunAsync {
      case Left(_)  => sendClose.unsafeRun() // RFC How to avoid this call?
      case Right(_) => // Nothing to do here
    }
  }

  override protected def stageShutdown(): Unit = {
    deadSignal.map(_.set(true)).unsafeRun
    super.stageShutdown()
  }
}

object Http4sWSStage {
  def bufferingSegment(stage: Http4sWSStage): LeafBuilder[WebSocketFrame] = {
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
  }
}
