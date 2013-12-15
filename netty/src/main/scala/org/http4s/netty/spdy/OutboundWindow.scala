package org.http4s.netty.spdy

import org.http4s.netty.utils.StreamContext

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */

/** Interface for maintaining an outbound window */
trait OutboundWindow extends StreamOutput {

  protected def manager: StreamContext[_]

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
