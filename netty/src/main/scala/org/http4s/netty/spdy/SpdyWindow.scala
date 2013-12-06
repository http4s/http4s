package org.http4s.netty.spdy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import org.http4s.TrailerChunk

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
trait SpdyWindow {

  def initialWindow: Int

  def closeSpdyWindow(): Unit

  def getWindow(): Int

  def writeStreamEnd(streamid: Int, buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture

  def writeStreamBuffer(streamid: Int, buff: ByteBuf): ChannelFuture

  def updateWindow(delta: Int): Unit
}
