package org.http4s.netty.spdy

import scalaz.concurrent.Task
import org.http4s.netty.utils.SpdyStreamManager
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.{Promise, Future, ExecutionContext}
import org.http4s.netty.{ProcessWriter, Cancelled}
import org.http4s.{TrailerChunk, BodyChunk}
import scala.util.{Try, Failure, Success}

/**
 * @author Bryce Anderson
 *         Created on 12/10/13
 */
trait SpdyStream extends SpdyOutboundWindow with ProcessWriter { self: Logging =>

  private var isclosed = false
  private val outboundLock = new AnyRef
  private var outboundWindow: Int = initialOutboundWindow
  private var outboundGuard: WindowGuard = null

  type Repr <: SpdyStream

  protected def parent: SpdyOutboundWindow with SpdyInboundWindow with SpdyStreamManager[Repr]

  def streamid: Int

  implicit protected def ec: ExecutionContext

  def close(): Task[Unit] = {
    parent.streamFinished(streamid)
    closeSpdyOutboundWindow(Cancelled)
    Task.now()
  }

  def kill(cause: Throwable): Task[Unit] = {
    parent.streamFinished(streamid)
    closeSpdyOutboundWindow(cause)
    Task.now()
  }

  def getOutboundWindow(): Int = outboundWindow

  /** Close this SPDY window canceling any waiting promises */
  def closeSpdyOutboundWindow(cause: Throwable): Unit = outboundLock.synchronized {
    if (!isclosed) {
      isclosed = true
      if (outboundGuard != null) {
        outboundGuard.p.failure(cause)
        outboundGuard = null
      }
    }
  }

  /** If a request is canceled, this method should return a canceled future */
  protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any] =
    writeStreamChunk(streamid, chunk, flush)

  /** If a request is canceled, this method should return a canceled future */
  protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] =
    writeStreamEnd(streamid, chunk, t)

  def awaitingWindowUpdate: Boolean = outboundGuard != null

  @inline
  final def outboundWindowSize(): Int = outboundWindow

  // Kind of windy method
  def writeStreamEnd(streamid: Int, chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] =
  outboundLock.synchronized {
    assert(outboundGuard == null)

    logger.trace(s"Stream $streamid writing end of stream: $chunk, $t")

    if (isclosed) return Future.failed(Cancelled)

    if (chunk.length > outboundWindowSize()) { // Need to break it up
    val p = Promise[Any]
      writeStreamChunk(streamid, chunk, true).onComplete {
        case Success(a) => p.completeWith(parent.writeStreamEnd(streamid, BodyChunk(), t))
        case Failure(t) => p.failure(t)
      }
      p.future
    }
    else parent.writeStreamEnd(streamid, chunk, t)
  }

  def writeStreamChunk(streamid: Int, chunk: BodyChunk, flush: Boolean): Future[Any] = outboundLock.synchronized {
    assert(outboundGuard == null)

    if (isclosed) return Future.failed(Cancelled)

    logger.trace(s"Stream $streamid writing buffer of size ${chunk.length}")

    if (chunk.length > outboundWindowSize()) { // Need to break it up
    val (left, right) = chunk.splitAt(outboundWindowSize())
      val p = Promise[Any]
      sendDownstream(left).onComplete(new WindowGuard(streamid, right, p))
      p.future
    }
    else sendDownstream(chunk)
  }

  private def writeExcess(streamid: Int, chunk: BodyChunk, p: Promise[Any]): Unit = outboundLock.synchronized {
    assert(outboundGuard == null)

    if (isclosed) {
      p.failure(Cancelled)
      return
    }

    if (chunk.length > outboundWindowSize()) { // Need to break it up
      if (outboundWindowSize() > 0) {
        val (left, right) = chunk.splitAt(outboundWindowSize())
        sendDownstream(left).onComplete(new WindowGuard(streamid, right, p))
      }
      else outboundGuard = new WindowGuard(streamid, chunk, p)  // Cannot write any bytes, set the window guard
    }
    // There is enough room so just write and chain the future to the promise
    else p.completeWith(sendDownstream(chunk))
  }

  def updateOutboundWindow(delta: Int) = outboundLock.synchronized {
    logger.trace(s"Stream $streamid updated window by $delta")
    outboundWindow += delta
    if (outboundGuard != null && outboundWindowSize() > 0) {   // Send more chunks
    val g = outboundGuard
      outboundGuard = null
      writeExcess(g.streamid, g.remaining, g.p)
    }
  }

  @inline
  private def sendDownstream(chunk: BodyChunk): Future[Any] = {
    outboundWindow -= chunk.length
    parent.writeStreamChunk(streamid, chunk, true)
  }

  private class WindowGuard(val streamid: Int, val remaining: BodyChunk, val p: Promise[Any])
    extends Function[Try[Any], Unit] {
    def apply(t: Try[Any]): Unit = t match {
      case Success(_) =>
        outboundLock.synchronized {
          logger.trace(s"Stream $streamid is continuing")
          if (outboundWindowSize() > 0) writeExcess(streamid, remaining, p)
          else outboundGuard = this
        }

      case Failure(t) => p.failure(t)
    }

    override def toString: String = {
      s"WindowGuard($streamid, $remaining, $p)"
    }
  }

}
