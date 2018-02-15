package org.http4s.server.blaze

import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.util.Execution._
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.{Promise, Future}
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success}

import java.net.ProtocolException


private class WSFrameAggregator extends MidStage[WebSocketFrame, WebSocketFrame] {

  def name: String = "WebSocket Frame Aggregator"

  private var queue = new ArrayBuffer[WebSocketFrame]
  private var size = 0

  def readRequest(size: Int): Future[WebSocketFrame] = {
    val p = Promise[WebSocketFrame]
    channelRead(size).onComplete {
      case Success(f) => readLoop(f, p)
      case Failure(t) => p.failure(t)
    }(directec)
    p.future
  }

  private def readLoop(frame: WebSocketFrame, p: Promise[WebSocketFrame]): Unit = frame match {
    case _: Text => handleHead(frame, p)
    case _: Binary => handleHead(frame, p)

    case c: Continuation =>
      if (queue.isEmpty) {
        val e = new ProtocolException("Invalid state: Received a Continuation frame without accumulated state.")
        logger.error(e)("Invalid state")
        p.failure(e)
      } else {
        queue += frame
        size += frame.length
        if (c.last) compileFrame(p)  // We are finished with the segment, accumulate
        else channelRead().onComplete {
          case Success(f) => readLoop(f, p)
          case Failure(t) => p.failure(t)
        }(trampoline)
      }

    case f => p.success(f) // Must be a control frame, send it out
  }

  private def compileFrame(p: Promise[WebSocketFrame]): Unit = {
    val arr = new Array[Byte](size)
    size = 0

    val msgs = queue
    queue = new ArrayBuffer[WebSocketFrame](msgs.size + 10)

    msgs.foldLeft(0) { (i, f) =>
      System.arraycopy(f.data, 0, arr, i, f.data.length)
      i + f.data.length
    }

    val msg = msgs.head match {
      case _: Text => Text(arr)
      case _: Binary => Binary(arr)
      case f => sys.error(s"Shouldn't get here. Wrong type: $f")
    }

    p.success(msg)
  }

  private def handleHead(frame: WebSocketFrame, p: Promise[WebSocketFrame]): Unit = {
    if (!queue.isEmpty) {
      val e = new ProtocolException(s"Invalid state: Received a head frame with accumulated state: ${queue.length} frames")
      logger.error(e)("Invalid state")
      size = 0
      queue.clear()
      p.failure(e)
    } else if(frame.last) p.success(frame)    // Head frame that is complete
    else {         // Need to start aggregating
      size += frame.length
      queue += frame
      channelRead().onComplete {
        case Success(f) => readLoop(f, p)
        case Failure(t) => p.failure(t)
      }(directec)
    }
  }

  // Just forward write requests
  def writeRequest(data: WebSocketFrame): Future[Unit] = channelWrite(data)
  override def writeRequest(data: Seq[WebSocketFrame]): Future[Unit] = channelWrite(data)
}
