package org.http4s.netty.utils

import scalaz.{\/-, -\/, \/}
import scalaz.stream.Process.End

import org.http4s.{BodyChunk, TrailerChunk, Chunk}
import java.util.{Queue, LinkedList}
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 *         Created on 11/27/13
 */
class ChunkHandler(val highWater: Int) extends Logging {

  type CB = Throwable \/ Chunk => Unit
  private val lock = new AnyRef
  private var bodyChunk: BodyChunk = null
  private var trailer: TrailerChunk = null
  private var cb: CB = null
  private var endThrowable: Throwable = null
  private var closed = false

  def isEmpty: Boolean = bodyChunk == null && trailer == null

  override def toString() = {
    s"${this.getClass.getName}(" +
      (if (bodyChunk != null) s"${bodyChunk.length}, " else "") +
      (if (trailer != null) trailer.toString() else "") + ")"
  }

  def queueSize(): Int = {
    if (bodyChunk != null) bodyChunk.length
    else if (cb != null) -1
    else 0
  }

  def onQueueFull(): Unit = {}

  def isClosed(): Boolean = closed

  /** Called when a chunk is sent out. This method will be called synchronously AFTER the cb is fired */
  def onBytesSent(n: Int): Unit = {}

  def close(trailer: TrailerChunk): Unit = lock.synchronized {
    enque(trailer)
    close()
  }

  def close(): Unit = lock.synchronized {
    logger.trace("Closing ChunkHandler")
    if (!isClosed) {
      closed = true
      endThrowable = End
      if (cb != null) {
        try cb(-\/(endThrowable))
        finally cb = null
      }
    }
  }

  def kill(t: Throwable): Unit = lock.synchronized {
    if (!isClosed || !isEmpty) {
      closed = true
      endThrowable = t
      trailer = null

      if (bodyChunk != null) {
        onBytesSent(bodyChunk.length)
        bodyChunk = null
      }

      if (cb != null) {
        try cb(-\/(t))
        finally cb = null
      }
    }
  }

  def request(cb: CB): Unit = lock.synchronized {
    logger.trace("Requesting data.")
    assert(this.cb == null)
    if (isEmpty) {
      if (isClosed()) {
        cb(-\/(endThrowable))
      } else {
        this.cb = cb
      }

    }

    else {   // Not empty

      if (bodyChunk != null) {
        try cb(\/-(bodyChunk))
        finally {
          onBytesSent(bodyChunk.length)
          bodyChunk = null
        }
      }
      else {  // Must be the trailer
        try cb(\/-(trailer))
        finally trailer = null
      }
    }
  }

  /** enqueues a chunk if the channel is open
    *
    * @param chunk Chunk to enqueue
    * @return -1 if the queue is closed, else the current queue size (may be 0 if a cb was present)
    */
  def enque(chunk: Chunk): Int = lock.synchronized {

    logger.trace(s"Enqueing chunk: $chunk")

    if (closed) return -1
    if (this.cb != null) {
      assert(isEmpty)
      val cb = this.cb
      this.cb = null

      try cb(\/-(chunk))
      finally onBytesSent(chunk.length)

    } else chunk match {
      case chunk: BodyChunk =>
        if (bodyChunk == null) {
          bodyChunk = chunk
        }
        else bodyChunk = bodyChunk ++ chunk   // Concat to the previous body chunk

        if (queueSize() >= highWater) onQueueFull()

      case chunk: TrailerChunk =>
        assert(trailer == null)
        trailer = chunk
    }
    queueSize()
  }
}