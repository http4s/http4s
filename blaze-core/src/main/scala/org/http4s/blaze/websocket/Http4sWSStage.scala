package org.http4s
package blaze
package websocket

import org.http4s.websocket.WebsocketBits._

import scala.util.{Failure, Success}
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.{websocket => ws4s}

import scalaz.stream._
import scalaz.concurrent._
import scalaz.{\/, \/-, -\/}

import pipeline.{TrunkBuilder, LeafBuilder, Command, TailStage}
import pipeline.Command.EOF

class Http4sWSStage(ws: ws4s.Websocket) extends TailStage[WebSocketFrame] {
  def name: String = "Http4s WebSocket Stage"
  
  private val dead = async.signalOf(false)

  //////////////////////// Source and Sink generators ////////////////////////

  def snk: Sink[Task, WebSocketFrame] = sink.lift { frame =>
    Task.async[Unit] { cb =>
      channelWrite(frame).onComplete {
        case Success(res) => cb(\/-(res))
        case Failure(Command.EOF) => cb(-\/(Cause.Terminated(Cause.End)))
        case Failure(t) => cb(-\/(t))
      }(directec)
    }
  }
  
  def inputstream: Process[Task, WebSocketFrame] = {
    val t = Task.async[WebSocketFrame] { cb =>
      def go(): Unit = channelRead().onComplete {
        case Success(ws) => ws match {
            case Close(_)    =>
              dead.set(true).run
              sendOutboundCommand(Command.Disconnect)
              cb(-\/(Cause.Terminated(Cause.End)))

            // TODO: do we expect ping frames here?
            case Ping(d)     =>  channelWrite(Pong(d)).onComplete {
              case Success(_)   => go()
              case Failure(EOF) => cb(-\/(Cause.Terminated(Cause.End)))
              case Failure(t)   => cb(-\/(t))
            }(trampoline)

            case Pong(_)     => go()
            case f           => cb(\/-(f))
          }

        case Failure(Command.EOF) => cb(-\/(Cause.Terminated(Cause.End)))
        case Failure(e)           => cb(-\/(e))
      }(trampoline)

      go()
    }
    Process.repeatEval(t)
  }

  //////////////////////// Startup and Shutdown ////////////////////////

  override protected def stageStartup(): Unit = {
    super.stageStartup()

    // A latch for shutting down if both streams are closed.
    val count = new java.util.concurrent.atomic.AtomicInteger(2)

    val onFinish: \/[Throwable,Any] => Unit = {
      case \/-(_) =>
        logger.trace("WebSocket finish signaled")
        if (count.decrementAndGet() == 0) {
          logger.trace("Closing WebSocket")
          sendOutboundCommand(Command.Disconnect)
        }
      case -\/(t) =>
        logger.error(t)("WebSocket Exception")
        sendOutboundCommand(Command.Disconnect)
    }
    
    (dead.discrete).wye(ws.exchange.read.to(snk))(wye.interrupt).run.runAsync(onFinish)

    // The sink is a bit more complicated
    val discard: Sink[Task, WebSocketFrame] = Process.constant(_ => Task.now(()))

    // if we never expect to get a message, we need to make sure the sink signals closed
    val routeSink: Sink[Task, WebSocketFrame] = ws.exchange.write match {
      case Process.Halt(Cause.End) => onFinish(\/-(())); discard
      case Process.Halt(e)   => onFinish(-\/(Cause.Terminated(e))); ws.exchange.write
      case s => s ++ Process.await(Task{onFinish(\/-(()))})(_ => discard)
    }
    
    inputstream.to(routeSink).run.runAsync(onFinish)
  }

  override protected def stageShutdown(): Unit = {
    dead.set(true).run
    super.stageShutdown()
  }
}

object Http4sWSStage {
  def bufferingSegment(stage: Http4sWSStage): LeafBuilder[WebSocketFrame] = {
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
  }
}
