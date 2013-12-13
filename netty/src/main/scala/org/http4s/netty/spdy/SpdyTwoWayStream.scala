package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging

import org.http4s.netty.utils.ChunkHandler
import org.http4s.{TrailerChunk, Chunk}

import scalaz.concurrent.Task
import scalaz.stream.Process
import org.http4s.netty.{Cancelled, NettySupport}

/**
 * @author Bryce Anderson
 *         Created on 12/13/13
 */
trait SpdyTwoWayStream extends NettySpdyServerStream with SpdyInboundWindow { self: Logging =>

  private val inboundChunkHandler = new ChunkHandler(initialWindow) {
    override def onBytesSent(n: Int): Unit = {
      incrementWindow(n)
      parent.incrementWindow(n)
    }
  }

  override def close(): Task[Unit] = {
    if (queueSize() > 0) parent.incrementWindow(queueSize())
    inboundChunkHandler.kill(Cancelled)
    super.close()
  }

  override def kill(t: Throwable): Task[Unit] = {
    if (queueSize() > 0) parent.incrementWindow(queueSize())
    inboundChunkHandler.kill(t)
    super.kill(t)
  }

  def inboundProcess: Process[Task, Chunk] = NettySupport.makeProcess(inboundChunkHandler)

  def closeInbound(trailer: TrailerChunk): Unit = inboundChunkHandler.close(trailer)

  def closeInbound(): Unit = inboundChunkHandler.close()

  def queueSize(): Int = inboundChunkHandler.queueSize()

  def enqueue(chunk: Chunk): Int = inboundChunkHandler.enque(chunk)

}
