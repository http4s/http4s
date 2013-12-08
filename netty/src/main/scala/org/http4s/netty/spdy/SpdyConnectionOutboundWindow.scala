package org.http4s.netty.spdy

import java.util.LinkedList

import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelFuture, ChannelPromise}
import io.netty.buffer.ByteBuf
import org.http4s.TrailerChunk
import io.netty.handler.codec.spdy.{DefaultSpdyHeadersFrame, DefaultSpdyDataFrame}
import org.http4s.netty.Cancelled
import org.http4s.netty.utils.SpdyConstants

import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
trait SpdyConnectionOutboundWindow extends SpdyOutboundWindow { self: Logging =>

  private var outboundWindow: Int = initialWindow
  private var connOutboundQueue = new LinkedList[StreamData]()

  def ctx: ChannelHandlerContext

  def getOutboundWindow(): Int = outboundWindow

  def writeStreamEnd(streamid: Int, buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture = connOutboundQueue.synchronized {
    if (buff.readableBytes() >= outboundWindow) {
      val p = ctx.newPromise()
      writeStreamBuffer(streamid, buff).addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          if (future.isSuccess) {
            if (!t.isEmpty) {
              val msg = new DefaultSpdyHeadersFrame(streamid)
              msg.setLast(true)
              t.get.headers.foreach( h => msg.headers().add(h.name.toString, h.value) )
              ctx.writeAndFlush(msg)
            }
            else {
              val msg = new DefaultSpdyDataFrame(streamid)
              msg.setLast(true)
              ctx.writeAndFlush(msg)
            }
            p.setSuccess()
          }
          else if (future.isCancelled) p.setFailure(new Cancelled(ctx.channel))
          else p.setFailure(future.cause())
        }
      })
      p
    }
    else {
      if (t.isDefined) {
        if (buff.readableBytes() > 0) {
          writeOutboundBodyBuff(streamid, buff, false, true) // Don't flush
        }
        val msg = new DefaultSpdyHeadersFrame(streamid)
        msg.setLast(true)
        t.get.headers.foreach( h => msg.headers().add(h.name.toString, h.value) )
        ctx.writeAndFlush(msg)
      }
      else writeOutboundBodyBuff(streamid, buff, true, true)
    }
  }

  def writeStreamBuffer(streamid: Int, buff: ByteBuf): ChannelFuture = connOutboundQueue.synchronized {
    logger.trace(s"Writing buffer: ${buff.readableBytes()}, windowsize: $outboundWindow")
    if (buff.readableBytes() > outboundWindow) {
      val p = ctx.newPromise()
      if (outboundWindow > 0) {
        val b = ctx.alloc().buffer(outboundWindow, outboundWindow)
        buff.readBytes(b)
        writeOutboundBodyBuff(streamid, b, false, true)
      }
      connOutboundQueue.addLast(StreamData(streamid, buff, p))
      p
    }
    else writeOutboundBodyBuff(streamid, buff, false, true)
  }

  def updateOutboundWindow(delta: Int): Unit = connOutboundQueue.synchronized {
    logger.trace(s"Updating connection window, delta: $delta, new: ${outboundWindow + delta}")
    outboundWindow += delta
    while (!connOutboundQueue.isEmpty && outboundWindow > 0) {   // Send more chunks
      val next = connOutboundQueue.poll()
      if (next.buff.readableBytes() > outboundWindow) { // Can only send part
        val b = ctx.alloc().buffer(outboundWindow, outboundWindow)
        next.buff.readBytes(b)
        writeOutboundBodyBuff(next.streamid, b, false, true)
        connOutboundQueue.addFirst(next)  // prepend to the queue
        return
      }
      else {   // write the whole thing and get another chunk
        writeOutboundBodyBuff(next.streamid, next.buff, false, connOutboundQueue.isEmpty || outboundWindow >= 0)
        next.p.setSuccess()
        // continue the loop
      }
    }
  }

  // Should only be called from inside the synchronized methods
  private def writeOutboundBodyBuff(streamid: Int, buff: ByteBuf, islast: Boolean, flush: Boolean): ChannelFuture = {
    outboundWindow -= buff.readableBytes()

    // Don't exceed maximum frame size
    while(buff.readableBytes() > SpdyConstants.SPDY_MAX_LENGTH) {
      val b = ctx.alloc().buffer(SpdyConstants.SPDY_MAX_LENGTH, SpdyConstants.SPDY_MAX_LENGTH)
      val msg = new DefaultSpdyDataFrame(streamid, b)
      ctx.write(msg)
    }

    val msg = new DefaultSpdyDataFrame(streamid, buff)
    msg.setLast(islast)
    if (flush) ctx.writeAndFlush(msg)
    else ctx.write(msg)
  }

  private case class StreamData(streamid: Int, buff: ByteBuf, p: ChannelPromise)
}
