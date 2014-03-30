package org.http4s.blaze
package websocket

import org.http4s.blaze.pipeline.{TrunkBuilder, LeafBuilder, Command, TailStage}
import org.http4s.blaze.pipeline.stages.http.websocket.WebSocketDecoder._
import scala.util.{Failure, Success}
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.{websocket => ws4s}

import scalaz.stream.Process
import scalaz.stream.Process._
import scalaz.{\/, \/-, -\/}
import scalaz.concurrent.Task
import org.http4s.blaze.pipeline.stages.http.websocket.WebSocketDecoder
import org.http4s.blaze.pipeline.Command.EOF

/**
 * Created by Bryce Anderson on 3/30/14.
 */

class Http4sWSStage(ws: ws4s.Websocket) extends TailStage[WebSocketFrame] {
  def name: String = "Http4s WebSocket Stage"

  @volatile private var alive = true

  //////////////////////// Translation functions ////////////////////////

  private def ws4sToBlaze(msg: ws4s.WSFrame): WebSocketFrame = msg match {
    case ws4s.Text(msg) => Text(msg)
    case ws4s.Binary(msg) => Binary(msg)
  }

  private def blazeTows4s(msg: WebSocketFrame): ws4s.WSFrame = msg match {
    case Text(msg, _)   => ws4s.Text(msg)
    case Binary(msg, _) => ws4s.Binary(msg)
    case f =>
      sendOutboundCommand(Command.Disconnect)
      sys.error(s"Frame type '$f' not understood")
  }

  //////////////////////// Source and Sink generators ////////////////////////

  def sink: Sink[Task, ws4s.WSFrame] = {
    def go(frame: ws4s.WSFrame): Task[Unit] = {
      Task.async { cb =>
        if (!alive) cb(-\/(End))
        else {
          channelWrite(ws4sToBlaze(frame)).onComplete {
            case Success(_)           => cb(\/-(()))
            case Failure(Command.EOF) => cb(-\/(End))
            case Failure(t)           => cb(-\/(t))
          }(directec)
        }
      }
    }

    Process.constant(go)
  }

  def inputstream: Process[Task, ws4s.WSFrame] = {
    val t = Task.async[ws4s.WSFrame] { cb =>
      def go(): Unit = channelRead().onComplete {
        case Success(ws) => ws match {
            case Close(_)    =>
              alive = false
              sendOutboundCommand(Command.Disconnect)
              cb(-\/(End))

            // TODO: do we expect ping frames here?
            case Ping(d)     =>  channelWrite(Pong(d)).onComplete{
              case Success(_)   => go()
              case Failure(EOF) => cb(-\/(End))
              case Failure(t)   => cb(-\/(t))
            }(trampoline)

            case Pong(_)     => go()  // TODO: should we deal with these?
            case f           => cb(\/-(blazeTows4s(f)))
          }

        case Failure(Command.EOF) => cb(-\/(End))
        case Failure(e)           => cb(-\/(e))
      }(directec)

      go()
    }

    repeatEval(t)
  }


  //////////////////////// Startup and Shutdown ////////////////////////

  override protected def stageStartup(): Unit = {
    super.stageStartup()

    val count = new java.util.concurrent.atomic.AtomicInteger(2)

    val onFinish: \/[Throwable,Any] => Unit = {
      case \/-(_) => if (count.decrementAndGet() == 0) sendOutboundCommand(Command.Disconnect)
      case -\/(_) => sendOutboundCommand(Command.Disconnect)
    }

    ws.source.through(sink).run.runAsync(onFinish)
    inputstream.to(ws.sink).run.runAsync(onFinish)
  }

  override protected def stageShutdown(): Unit = {
    alive = false
    super.stageShutdown()
  }
}

object Http4sWSStage {
  def bufferingSegment(stage: Http4sWSStage): LeafBuilder[WebSocketFrame] = {
    WebSocketDecoder
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
  }
}
