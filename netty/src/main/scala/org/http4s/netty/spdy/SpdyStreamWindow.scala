package org.http4s.netty.spdy


import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandlerContext, ChannelFuture, ChannelFutureListener, ChannelPromise}
import io.netty.handler.codec.spdy.{DefaultSpdyDataFrame, DefaultSpdyHeadersFrame}

import org.http4s.netty.Cancelled
import org.http4s.TrailerChunk

import com.typesafe.scalalogging.slf4j.Logging
import scalaz.concurrent.Task

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
trait SpdyStreamWindow extends SpdyWindow { self: Logging =>

  private var isclosed = false
  private val outboundLock = new AnyRef
  private var outboundWindow: Int = initialWindow
  private var guard: WindowGuard = null

  def streamid: Int

  protected def ctx: ChannelHandlerContext

  def getWindow(): Int = outboundWindow

  def closeSpdyWindow(): Unit = outboundLock.synchronized {
    if (!isclosed) {
      isclosed = true
      if (guard != null) {
        guard.p.cancel(true)
        guard = null
      }
    }
  }

  /** Called when needed to to write body bytes if the outboundWindow is sufficient
    *
    * @param buff buffer to be written
    * @return ChannelFuture representing the completed write
    */
  protected def writeBodyBytes(buff: ByteBuf): ChannelFuture

  protected def writeEndBytes(buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture

  def awaitingWindowUpdate: Boolean = guard != null

  def windowSize(): Int = outboundWindow

  // Kind of windy method
  def writeStreamEnd(streamid: Int, buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture = outboundLock.synchronized {
    assert(guard == null)

    if (isclosed) {
      val p = ctx.channel().newPromise()
      p.cancel(true)
      return p
    }

    if (buff.readableBytes() > outboundWindow) { // Need to break it up
      val p = ctx.channel().newPromise()

      writeStreamBuffer(streamid, buff).addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          if (future.isSuccess) {
            if (t.isDefined) {
              val msg = new DefaultSpdyHeadersFrame(streamid)
              t.get.headers.foreach(h => msg.headers().add(h.name.toString, h.value))
              msg.setLast(true)
              ctx.writeAndFlush(msg)
              p.setSuccess()
            }
            else {
              val msg = new DefaultSpdyDataFrame(streamid)
              msg.setLast(true)
              ctx.writeAndFlush(msg)
              p.setSuccess()
            }
          }
        }
      })
      p
    }
    else writeEndBytes(buff, t)
  }

  def writeStreamBuffer(streamid: Int, buff: ByteBuf): ChannelFuture = outboundLock.synchronized {
    assert(guard == null)

    if (isclosed) {
      val p = ctx.channel().newPromise()
      p.cancel(true)
      return p
    }

    logger.trace(s"Stream $streamid writing buffer of size ${buff.readableBytes()}")

    if (buff.readableBytes() > outboundWindow) { // Need to break it up
    val nbuff = ctx.alloc().buffer(outboundWindow, outboundWindow)
      val p = ctx.channel().newPromise()
      buff.readBytes(nbuff)
      outboundWindow = 0
      writeBodyBytes(nbuff).addListener(new WindowGuard(streamid, buff, p))
      p
    }
    else {
      outboundWindow -= buff.readableBytes()
      writeBodyBytes(buff)
    }
  }

  private def writeExcess(streamid: Int, buff: ByteBuf, p: ChannelPromise): Unit = outboundLock.synchronized {
    assert(guard == null)

    if (isclosed) {
      p.cancel(true)
      return
    }

    if (buff.readableBytes() > outboundWindow) { // Need to break it up
      if (outboundWindow > 0) {
        val nbuff = ctx.alloc().buffer(outboundWindow, outboundWindow)
        buff.readBytes(nbuff)
        outboundWindow = 0
        writeBodyBytes(nbuff).addListener(new WindowGuard(streamid, buff, p))
      }
      else guard = new WindowGuard(streamid, buff, p)  // Cannot write any bytes, set the window guard
    }
    else {   // There is enough room so just write and chain the future to the promise
      outboundWindow -= buff.readableBytes()
      writeBodyBytes(buff).addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          if (future.isSuccess) p.setSuccess()
          else if (future.isCancelled) p.setFailure(new Cancelled(future.channel()))
          else p.setFailure(future.cause())
        }
      })
    }
  }

  def updateWindow(delta: Int) = outboundLock.synchronized {
    logger.trace(s"Stream $streamid updated window by $delta")
    outboundWindow += delta
    if (guard != null && outboundWindow > 0) {   // Send more chunks
    val g = guard
      guard = null
      writeExcess(g.streamid, g.remaining, g.p)
    }
  }

  private class WindowGuard(val streamid: Int, val remaining: ByteBuf, val p: ChannelPromise) extends ChannelFutureListener {
    def operationComplete(future: ChannelFuture) {
      if (future.isSuccess) outboundLock.synchronized {
        logger.trace(s"Stream $streamid is contining! ${windowSize} ----------------------------------------------------------")
        if (windowSize > 0) writeExcess(streamid, remaining, p)
        else guard = this
      }
      else if (future.isCancelled) p.setFailure(new Cancelled(future.channel()))
      else p.setFailure(future.cause())
    }

    override def toString: String = {
      s"WindowGuard($streamid, ByteBuf(${remaining.readableBytes()}), p)"
    }
  }

}
