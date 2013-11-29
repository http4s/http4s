package org.http4s.netty.utils

import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */
trait SpdyStreamManager extends Logging {
  private val currentID = new AtomicInteger(0)
  private var maxStreams = Integer.MAX_VALUE >> 1   // 2^31

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

  def lastOpenedStream: Int = currentID.get()

  def setMaxStreams(n: Int): Int = {
    if (n < maxStreams) maxStreams = n
    maxStreams
  }

  def streamID(id: Int) {
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

}
