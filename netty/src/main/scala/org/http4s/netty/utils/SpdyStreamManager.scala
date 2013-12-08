package org.http4s.netty.utils

import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */

/** Manages the stream ID's for the SPDY protocol
  * In a separate trait to clean up the SpdyNettyHandler class
  */
trait SpdyStreamManager { self: Logging =>
  private val currentStreamID = new AtomicInteger(0)
  private var maxStreams = Integer.MAX_VALUE    // 2^31
  private var initialStreamSize = 64*1024       // 64KB

  /** Get a new stream ID for a new server initiated stream
    * @return ID to assign to the stream
    */
  def newServerStreamID(): Int = newStreamID(true)

  /** Get a new stream ID for a new client initiated stream
    * @return ID to assign to the stream
    */
  def newClientStreamID(): Int = newStreamID(false)

  /** Tell about the current request stream ID and set it if it is the new highest stream ID
    * @param id ID if the SynStream request
    */
  def setCurrentStreamID(id: Int) {
    def go(): Unit = {
      val current = currentStreamID.get()
      if (id > current) {  // Valid ID
        if(!currentStreamID.compareAndSet(current, id)) go()
        else ()
      }
      else logger.warn(s"StreamID $id is less than the current: $current")
    }
    go()
  }

  /** Get the ID of the latest known SPDY stream
    * @return the stream ID
    */
  def lastOpenedStream: Int = currentStreamID.get()

  /** Set the maximum number of streams
    *
    * @param n the requested maximum number of streams
    * @return the set maximum number of streams
    */
  def setMaxStreams(n: Int): Int = {
    if (n < maxStreams) maxStreams = n
    maxStreams
  }

  /** The initial size of SPDY stream windows
    *
    * @return the initial window size
    */
  def initialWindow: Int = initialStreamSize

  /** Set the initial window size of the SPDY stream
    * @param n size in bytes of the initial window
    */
  protected def setInitialWindow(n: Int): Unit = {
    initialStreamSize = n
  }

  private def newStreamID(isServer: Boolean): Int = {  // Need an odd integer
  val modulo = if (isServer) 0 else 1
    def go(): Int = {
      val current = currentStreamID.get()
      val inc = if (current % 2 == modulo) 2 else 1
      val next = current + inc
      if (!currentStreamID.compareAndSet(current, next)) go()
      // Make sure we don't pass the maximum number of streams
      else if (next < Integer.MAX_VALUE) next else -1
    }
    go()
  }
}
