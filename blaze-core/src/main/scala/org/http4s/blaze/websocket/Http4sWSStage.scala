package org.http4s
package blaze
package websocket

import fs2.async.mutable.Signal
import org.http4s.websocket.WebsocketBits._

import scala.util.{Failure, Success}
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.blaze.util.Execution.{directec, trampoline}
//import org.http4s.internal.compatibility._
import org.http4s.{websocket => ws4s}

//import scalaz.concurrent._
//import scalaz.{\/, \/-, -\/}
import fs2.async
import fs2._

import pipeline.{TrunkBuilder, LeafBuilder, Command, TailStage}
import pipeline.Command.EOF

class Http4sWSStage(ws: ws4s.Websocket)(implicit val strategy: Strategy) extends TailStage[WebSocketFrame] {
  def name: String = "Http4s WebSocket Stage"

  def log[A](prefix: String): Pipe[Task, A, A] = _.evalMap{a => Task.delay {println(s"$prefix> $a"); a} }

  private val deadSignal: Task[Signal[Task, Boolean]] = async.signalOf[Task, Boolean](false)

  //////////////////////// Source and Sink generators ////////////////////////

  def snk: Sink[Task, WebSocketFrame] = _.evalMap { frame =>
    Task.async[Unit] { cb =>
      channelWrite(frame).onComplete {
        case Success(res) => cb(Right(res))
        case Failure(t@Command.EOF) => cb(Left(t))
        case Failure(t) => cb(Left(t))
      }(directec)
    }
  }

  def inputstream: Stream[Task, WebSocketFrame] = {
    val t = Task.async[WebSocketFrame] { cb =>
      def go(): Unit = channelRead().onComplete {
        case Success(ws) => ws match {
            case Close(_)    =>
              for {
                _ <- deadSignal.map(_.set(true))
              } yield {
                sendOutboundCommand(Command.Disconnect)
                cb(Left(new RuntimeException("a")))
              }

            // TODO: do we expect ping frames here?
            case Ping(d)     =>  channelWrite(Pong(d)).onComplete {
              case Success(_)   => go()
              case Failure(EOF) => cb(Left(new RuntimeException("b")))
              case Failure(t)   => cb(Left(t))
            }(trampoline)

            case Pong(_)     => go()
            case f           => cb(Right(f))
          }

        case Failure(Command.EOF) => cb(Left(new RuntimeException("c")))
        case Failure(e)           => cb(Left(e))
      }(trampoline)

      go()
    }
    Stream.repeatEval(t)//.onHalt(_.asHalt)
  }

  //////////////////////// Startup and Shutdown ////////////////////////

  override protected def stageStartup(): Unit = {
    super.stageStartup()

    // A latch for shutting down if both streams are closed.
    val count = new java.util.concurrent.atomic.AtomicInteger(2)

    val onFinish: Either[Throwable,Any] => Unit = {
      case Right(_) =>
        logger.trace("WebSocket finish signaled")
        if (count.decrementAndGet() == 0) {
          logger.trace("Closing WebSocket")
          sendOutboundCommand(Command.Disconnect)
        }
      case Left(t) =>
        logger.error(t)("WebSocket Exception")
        sendOutboundCommand(Command.Disconnect)
    }

    //dead.map(_.discrete.drain)//(wye.interrupt).run.unsafePerformAsync(onFinish) */
    /*
    // The sink is a bit more complicated
    val discard: Sink[Task, WebSocketFrame] = Process.constant(_ => Task.now(()))*/

    // If both streams are closed set the signal
    val onStreamFinalize: Task[Unit] =
      for {
        dec <- Task.delay(count.decrementAndGet())
        _   <- deadSignal.map(signal => if (dec == 0) signal.set(true))
      } yield ()

    // Task to send a close to the other endpoint
    val sendClose: Task[Unit] = Task.delay(sendOutboundCommand(Command.Disconnect))

    // RFC mergeHaltR means we close the socket when the input  stream stops
    // It used to be that we'd wait for both streams to close but now read is a Sink
    // Can we stop a Sink?
    val wsStream = for {
      dead   <- deadSignal
      in     = inputstream.through(log("input")).onFinalize(onStreamFinalize)
      out    = ws.read.through(log("output")).onFinalize(onStreamFinalize).to(snk)
      merged <- (in mergeHaltR out.drain).interruptWhen(dead).onFinalize(sendClose).run
    } yield merged
    wsStream.unsafeRunAsyncFuture()
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
