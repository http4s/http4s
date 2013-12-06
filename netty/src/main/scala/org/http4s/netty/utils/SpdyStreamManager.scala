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
  private val currentID = new AtomicInteger(0)
  private var maxStreams = Integer.MAX_VALUE >> 1   // 2^31
  private var initialStreamSize = 64*1024   // 64 KB

  /** Get an outgoing stream ID for the next push request
    * @return ID to assign to the stream
    */
  def newPushStreamID(): Int = {  // Need an odd integer
    def go(): Int = {
      val current = currentID.get()
      val inc = if (current % 2 == 0) 2 else 1
      val next = current + inc
      if (!currentID.compareAndSet(current, next)) go()
      else next
    }
      go()
  }

  /** Tell about the current request stream ID and set it if it is the new highest stream ID
    * @param id ID if the SynStream request
    */
  def setRequestStreamID(id: Int) {
    def go(): Unit = {
      val current = currentID.get()
      if (id > current) {  // Valid ID
        if(!currentID.compareAndSet(current, id)) go()
        else ()
      }
      else logger.warn(s"StreamID $id is less than the current: $current")
    }
    go()
  }

  /** Get the ID of the latest known SPDY stream
    * @return the stream ID
    */
  def lastOpenedStream: Int = currentID.get()

  /** Set the initial window size of the SPDY stream
    * @param n size in bytes of the initial window
    */
  protected def setInitialWindow(n: Int): Unit = {
    initialStreamSize = n
  }

  def initialWindow: Int = initialStreamSize

  def setMaxStreams(n: Int): Int = {
    if (n < maxStreams) maxStreams = n
    maxStreams
  }



}
