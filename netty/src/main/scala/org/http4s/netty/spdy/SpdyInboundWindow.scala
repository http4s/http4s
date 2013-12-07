package org.http4s.netty.spdy

/**
 * @author Bryce Anderson
 *         Created on 12/6/13
 */
trait SpdyInboundWindow {

  private val inboundLock = new AnyRef
  private var inboundWindowSize = initialInboundWindowSize
  private var inboundMaxWindow = initialInboundWindowSize
  private var inboundUpdateBuffer = 0

  def initialInboundWindowSize: Int

  def submitDeltaWindow(n: Int): Unit

  def getInboundWindow(): Int = inboundWindowSize

  def changeMaxWindow(newValue: Int): Int = inboundLock.synchronized {
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
      submitDeltaWindow(inboundUpdateBuffer)
      inboundUpdateBuffer = 0
    }

    inboundWindowSize += n
    inboundWindowSize
  }
}
