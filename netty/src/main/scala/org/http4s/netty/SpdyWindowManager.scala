package org.http4s.netty

import java.util.concurrent.atomic.AtomicInteger

import org.http4s.{TrailerChunk, Chunk}
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import org.http4s.netty.utils.{ChunkHandler, NettyOutput, SpdyValues}
import io.netty.handler.codec.spdy._
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.netty.NettySupport._
import org.http4s.TrailerChunk

import scalaz.stream.Process
import Process._
import scalaz.concurrent.Task

/**
 * @author Bryce Anderson
 *         Created on 12/1/13
 */
trait SpdyWindowManager extends NettyOutput[SpdyFrame] { self: SpdyStreamContext with Logging =>
  
  def parentHandler: SpdyNettyHandler

  def manager: ChunkHandler
  
  private val recvWindow = new AtomicInteger(parentHandler.initialStreamWindow)
  private val sendWindow = new AtomicInteger(parentHandler.initialStreamWindow)

  def spdyMessage(msg: SpdyFrame): Unit = msg match {     // the SpdyNettyHandler forwards messages to this method

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

    case windowupdate: SpdyWindowUpdateFrame =>
      val remaining = recvWindow.addAndGet(windowupdate.getDeltaWindowSize)

    case rst: SpdyRstStreamFrame => close()
  }

  override def writeBytes(tail: Process[Task, Chunk], buff: ByteBuf, ctx: ChannelHandlerContext, cb: CBType): Unit = {
    // Break up the chunk if we need to
    while (buff.readableBytes() > SpdyValues.SPDY_MAX_LENGTH) {
      val b = ctx.alloc().buffer(SpdyValues.SPDY_MAX_LENGTH, SpdyValues.SPDY_MAX_LENGTH)
      buff.readBytes(b)
      ctx.channel().write(bufferToMessage(b))
    }
    super.writeBytes(tail, buff, ctx, cb)
  }

}
