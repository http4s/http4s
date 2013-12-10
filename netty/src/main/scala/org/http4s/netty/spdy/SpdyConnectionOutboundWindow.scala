package org.http4s.netty.spdy

import java.util.LinkedList

import org.http4s.{BodyChunk, TrailerChunk}
import org.http4s.netty.utils.SpdyConstants

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.{Failure, Success}

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
trait SpdyConnectionOutboundWindow extends SpdyOutboundWindow { self: Logging =>

  private var outboundWindow: Int = initialWindow
  private val connOutboundQueue = new LinkedList[StreamData]()

  implicit protected def ec: ExecutionContext

  def getOutboundWindow(): Int = outboundWindow

  def connectWriteStreamEnd(streamid: Int, chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any]

  def connectionWriteBodyChunk(streamid: Int, chunk: BodyChunk, flush: Boolean): Future[Any]

  def writeStreamEnd(streamid: Int, chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] =
  connOutboundQueue.synchronized {
    if (chunk.length >= outboundWindow) {
      val p = Promise[Any]
      writeStreamChunk(streamid, chunk, true).onComplete {
        case Success(a) =>  p.completeWith(connectWriteStreamEnd(streamid, BodyChunk(), t))
        case Failure(t) => p.failure(t)
      }
      p.future
    }
    else connectWriteStreamEnd(streamid, chunk, t)
  }

  def writeStreamChunk(streamid: Int, chunk: BodyChunk, flush: Boolean): Future[Any] = {
    connOutboundQueue.synchronized {
      logger.trace(s"Writing chunk: $chunk, windowsize: $outboundWindow")
      if (chunk.length > outboundWindow) {
        val p = Promise[Any]
        if (outboundWindow > 0) {
          val (left, right) = chunk.splitAt(outboundWindow)
          writeOutboundBodyBuff(streamid, left, true)
          connOutboundQueue.addLast(StreamData(streamid, right, p))
        }
        else connOutboundQueue.addLast(StreamData(streamid, chunk, p))
        p.future
      }
      else writeOutboundBodyBuff(streamid, chunk, flush)
    }
  }

  // TODO: this method could cause problems on backends that require each future to resolve
  // TODO  before sending another chunk
  def updateOutboundWindow(delta: Int): Unit = connOutboundQueue.synchronized {
    logger.trace(s"Updating connection window, delta: $delta, new: ${outboundWindow + delta}")
    outboundWindow += delta
    while (!connOutboundQueue.isEmpty && outboundWindow > 0) {   // Send more chunks
      val next = connOutboundQueue.poll()
      if (next.chunk.length > outboundWindow) { // Can only send part
        val (left, right) = next.chunk.splitAt(outboundWindow)
        writeOutboundBodyBuff(next.streamid, left, false)
        connOutboundQueue.addFirst(StreamData(next.streamid, right, next.p))  // prepend to the queue
        return
      }
      else {   // write the whole thing and get another chunk
        next.p.completeWith(writeOutboundBodyBuff(
                    next.streamid,
                    next.chunk,
                    connOutboundQueue.isEmpty || outboundWindow >= 0))
        // continue the loop
      }
    }
  }

  // Should only be called from inside the synchronized methods
  private def writeOutboundBodyBuff(streamid: Int, chunk: BodyChunk, flush: Boolean): Future[Any] = {
    outboundWindow -= chunk.length

    // Don't exceed maximum frame size
    def go(chunk: BodyChunk): Future[Any] = {
      if (chunk.length > SpdyConstants.SPDY_MAX_LENGTH) {
        val (left, right) = chunk.splitAt(SpdyConstants.SPDY_MAX_LENGTH)
        connectionWriteBodyChunk(streamid, left, false).flatMap(_ => go(right))
      }
      else connectionWriteBodyChunk(streamid, chunk, flush)
    }

    go(chunk)
  }

  private case class StreamData(streamid: Int, chunk: BodyChunk, p: Promise[Any])
}
