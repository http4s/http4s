package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture

import org.http4s.TrailerChunk
import org.http4s.netty. NettyOutput

/**
 * @author Bryce Anderson
 *         Created on 12/4/13
 */
trait SpdyStreamOutput extends NettyOutput with SpdyStreamOutboundWindow { self: Logging =>

  def streamid: Int

  protected def writeBodyBuffer(buff: ByteBuf): ChannelFuture = {
    writeStreamBuffer(streamid, buff)
  }

  protected def writeEnd(buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture = {
    writeStreamEnd(streamid, buff, t)
  }

}
