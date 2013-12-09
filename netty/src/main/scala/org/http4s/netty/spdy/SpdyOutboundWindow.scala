package org.http4s.netty.spdy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import org.http4s.TrailerChunk

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */

/** Interface for maintaining an outbound window */
trait SpdyOutboundWindow {

  /** the initial size of the outbout window */
  def initialWindow: Int

  /** close the outbound window */
  def closeSpdyOutboundWindow(): Unit

  /** get the current window size
    *
    * @return current outbound window size
    */
  def getOutboundWindow(): Int

  /** Write the end of a stream
    *
    * @param streamid ID of the stream to write to
    * @param buff last body buffer
    * @param t optional Trailer
    * @return a future which will resolve once the data has made ot past the window
    */
  def writeStreamEnd(streamid: Int, buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture

  /** Write data to the stream
    *
    * @param streamid ID of the stream to write to
    * @param buff buffer of data to write
    * @return a future which will resolve once the data has made ot past the window
    */
  def writeStreamBuffer(streamid: Int, buff: ByteBuf): ChannelFuture

  /** Method to change the window size of this stream
    *
    * @param delta change in window size
    */
  def updateOutboundWindow(delta: Int): Unit
}
