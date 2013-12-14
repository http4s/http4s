package org.http4s.netty.spdy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import org.http4s.{BodyChunk, TrailerChunk}
import scala.concurrent.Future

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */

/** Interface for maintaining an outbound window */
trait SpdyOutboundWindow extends SpdyOutput {

  /** the initial size of the outbout window */
  def initialOutboundWindow: Int

  /** close the outbound window */
  def closeSpdyOutboundWindow(cause: Throwable): Unit

  /** get the current window size
    *
    * @return current outbound window size
    */
  def getOutboundWindow(): Int

  /** Method to change the window size of this stream
    *
    * @param delta change in window size
    */
  def updateOutboundWindow(delta: Int): Unit
}
