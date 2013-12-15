package org.http4s.netty.spdy

import org.http4s.netty.utils.StreamContext

/**
 * @author Bryce Anderson
 *         Created on 12/6/13
 */
trait InboundWindow {

  private val inboundLock = new AnyRef
  private var inboundWindowSize = manager.initialInboundWindow
  private var inboundMaxWindow = manager.initialInboundWindow
  private var inboundUpdateBuffer = 0

  protected def manager: StreamContext[_]

  protected def submitDeltaInboundWindow(n: Int): Unit

  def getInboundWindow(): Int = inboundWindowSize

  def changeMaxInboundWindow(newValue: Int): Int = inboundLock.synchronized {
    val old = inboundMaxWindow
    inboundMaxWindow = newValue
    inboundWindowSize += newValue - old
    inboundWindowSize
  }

  def decrementWindow(n: Int): Int = inboundLock.synchronized {
    inboundWindowSize -= n
    inboundWindowSize
  }

  def incrementWindow(n: Int): Int = inboundLock.synchronized {
    inboundUpdateBuffer += n

    if (inboundUpdateBuffer > inboundMaxWindow / 2) {
      submitDeltaInboundWindow(inboundUpdateBuffer)
      inboundUpdateBuffer = 0
    }

    inboundWindowSize += n
    inboundWindowSize
  }
}
