package org.http4s.blaze
package websocket

import org.http4s.blaze.pipeline.{TrunkBuilder, LeafBuilder, Command, TailStage}
import org.http4s.blaze.pipeline.stages.http.websocket.WebSocketDecoder._
import scala.util.{Failure, Success}
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.blaze.util.Execution.directec
import org.http4s.websocket.Websocket
import org.http4s.{CharacterSet, BodyChunk}

import scalaz.stream.Process
import scalaz.stream.Process._
import scalaz.{\/, \/-, -\/}
import scalaz.concurrent.Task
import org.http4s.blaze.pipeline.stages.http.websocket.WebSocketDecoder

/**
 * Created by Bryce Anderson on 3/30/14.
 */

class Http4sWSStage(ws: Websocket) extends TailStage[WebSocketFrame] {
  def name: String = "Http4s WebSocket Stage"

  /////////////////////////////////////////////////////////////

  @volatile private var alive = true

  def sink: Sink[Task, BodyChunk] = {
    def go(chunk: BodyChunk): Task[Unit] = {
      Task.async { cb =>
        if (!alive) cb(-\/(End))
        else channelWrite(Text(chunk.decodeString(CharacterSet.`UTF-8`))).onComplete {
          case Success(_)           => cb(\/-(()))
          case Failure(Command.EOF) => cb(-\/(End))
          case Failure(t)           => cb(-\/(t))
        }(directec)
      }
    }

    Process.constant(go)
  }

  def inputstream: Process[Task, BodyChunk] = {
    val t = Task.async[BodyChunk] { cb =>
      channelRead().onComplete {
        case Success(ws) => ws match {
          case Text(msg, _)   => cb(\/-(BodyChunk(msg)))
          case Binary(msg, _) => cb(\/-(BodyChunk(msg)))
          case Close(_)       =>
            alive = false
            cb(-\/(End)) // TODO: we might want to shutdown
        }

        case Failure(Command.EOF) => cb(-\/(End))
        case Failure(e)           => cb(-\/(e))
      }(directec)
    }

    repeatEval(t)
  }



  override protected def stageStartup(): Unit = {
    super.stageStartup()

    val count = new java.util.concurrent.atomic.AtomicInteger(2)

    val onFinish: \/[Throwable,Any] => Unit = {
      case \/-(_) =>
        if (count.decrementAndGet() == 0)
          sendOutboundCommand(Command.Disconnect)

      case -\/(_) =>
        sendOutboundCommand(Command.Disconnect)
    }

    ws.source.through(sink).run.runAsync(onFinish)
    inputstream.to(ws.sink).run.runAsync(onFinish)
  }
}

object Http4sWSStage {
  def bufferingSegment(stage: Http4sWSStage): LeafBuilder[WebSocketFrame] = {
    WebSocketDecoder
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
  }
}
