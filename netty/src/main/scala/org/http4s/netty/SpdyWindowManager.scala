package org.http4s.netty

import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}

import org.http4s.{TrailerChunk, Chunk}
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import org.http4s.netty.utils.{ChunkHandler, NettyOutput, SpdyConstants}
import io.netty.handler.codec.spdy._
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.netty.NettySupport._
import org.http4s.TrailerChunk

import scalaz.stream.Process
import Process._
import scalaz.concurrent.Task
import java.util.concurrent.TimeUnit

/**
 * @author Bryce Anderson
 *         Created on 12/1/13
 */
trait SpdyWindowManager extends NettyOutput[SpdyFrame] { self: SpdyStreamContext with Logging =>
  
  def parentHandler: SpdyNettyHandler

  protected def ctx: ChannelHandlerContext

  // Signals the termination of this stream
  private var isalive = true

  // bits for managing inbound and outbound window sizes
  private val outboundLock = new AnyRef
  private var outboundWindow = parentHandler.initialStreamWindow
  private var continue: Continue = null

  val manager = new ChunkHandler(parentHandler.initialStreamWindow) {

    // Don't want to echo every time, just once we get in danger of reaching the high water point
    private var unaccountedBytes = 0


    override def onQueueFull(): Unit = {
      if (queueSize() < 1.25*highWater)
        logger.warn(s"Queue exceeding the window size limit. size:${queueSize()}, limit: $highWater")
      else {  // To much. Abort the connection

        // TODO: need to alert writeBytes that we are closing for business

        ctx.channel().writeAndFlush(SpdyConstants.FLOW_CONTROL_ERROR(streamid))
        kill(new Exception("Queue overflow."))
        isalive = false
        parentHandler.streamFinished(streamid)
      }
    }

    override def onBytesSent(delta: Int): Unit =  {
      unaccountedBytes += delta
      if (unaccountedBytes > 0.5*highWater) {
        ctx.channel().writeAndFlush(new DefaultSpdyWindowUpdateFrame(streamid, unaccountedBytes))
        unaccountedBytes = 0
      }
    }
  }

  

  // TODO: implement these in a more meaningful way
  def close(): Task[Unit] = {
    isalive = false
    manager.close()
    Task.now()
  }

  def kill(t: Throwable): Task[Unit] = {
    isalive = false
    manager.kill(t)
    Task.now()
  }
  

  def spdyMessage(ctx: ChannelHandlerContext, msg: SpdyFrame): Unit = msg match {     // the SpdyNettyHandler forwards messages to this method

    case chunk: SpdyDataFrame =>
      manager.enque(buffToBodyChunk(chunk.content()))
      if (chunk.isLast) manager.close()

    case headers: SpdyHeadersFrame =>
      logger.error("Header frames not supported yet. Dropping.")
      if (headers.isLast) {
        if (!headers.headers().isEmpty) {
          val headercollection = toHeaders(headers.headers)
          manager.close(TrailerChunk(headercollection))
        }
        else manager.close()
      }

    case windowupdate: SpdyWindowUpdateFrame => outboundLock.synchronized {
      outboundWindow += windowupdate.getDeltaWindowSize
      if (continue != null) {
        logger.trace(s"Restarting Continue $continue")
        val c = continue
        continue = null
        writeBytes(c.tail, c.buff, ctx, c.cb)
      }
    }

    case rst: SpdyRstStreamFrame => close()
  }

  override def writeBytes(tail: Process[Task, Chunk], buff: ByteBuf, ctx: ChannelHandlerContext, cb: CBType): Unit = {

    assert(continue == null)
    if (!isalive) {
      tail.kill
      return
    }

    // Make sure we have enough window to do this
    val windowAvailable = outboundLock.synchronized {
      if (buff.readableBytes() > outboundWindow) {
        val b = ctx.alloc().buffer(outboundWindow, outboundWindow)
        buff.readBytes(b)

        logger.trace(s"Waiting on WindowUpdate for ${b.readableBytes()} bytes")

        sizeAndWriteExcess(b)
        ctx.channel().writeAndFlush(bufferToMessage(b))
        val c = Continue(tail, buff, cb)
        continue = c

        // TODO: should we have a timeout of some sort?
//        val r = new Runnable {
//          def run() = {
//            DEATH
//          }
//        }
//        ctx.channel().eventLoop().schedule(r, 10, TimeUnit.SECONDS)

        false
      }
      else true
    }

    // Break up the chunk if we need to
    if (windowAvailable) {
      sizeAndWriteExcess(buff)
      super.writeBytes(tail, buff, ctx, cb)
    }

  }

  // Break off any chunks that are larger than the max, and send them, leaving the rest in the buffer
  private def sizeAndWriteExcess(buff: ByteBuf) {
    while (buff.readableBytes() > SpdyConstants.SPDY_MAX_LENGTH) {
      val b = ctx.alloc().buffer(SpdyConstants.SPDY_MAX_LENGTH, SpdyConstants.SPDY_MAX_LENGTH)
      buff.readBytes(b)
      ctx.channel().write(bufferToMessage(b))
    }
  }

  private case class Continue(tail: Process[Task, Chunk], buff: ByteBuf, cb: CBType)
}
