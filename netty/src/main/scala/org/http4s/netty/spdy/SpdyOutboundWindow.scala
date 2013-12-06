package org.http4s.netty.spdy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import org.http4s.TrailerChunk

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
trait SpdyOutboundWindow {

  def initialOutboundWindow: Int

  def closeSpdyWindow(): Unit

  def getOutboundWindow(): Int

  def writeStreamEnd(streamid: Int, buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture

  def writeStreamBuffer(streamid: Int, buff: ByteBuf): ChannelFuture

  def updateOutboundWindow(delta: Int): Unit
}
