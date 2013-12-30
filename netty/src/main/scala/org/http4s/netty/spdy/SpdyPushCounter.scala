package org.http4s.netty.spdy

import java.util.LinkedHashMap
import java.util.Map.Entry
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Bryce Anderson
 *         Created on 12/29/13
 */
class SpdyPushCounter(maxSize: Int) extends Logging {
  private val ref = new AtomicReference[LinkedHashMap[String, Object]](new LinkedHashMap[String, Object](maxSize/8) {
    override def removeEldestEntry(eldest: Entry[String, Object]) = this.size() > maxSize
  })

  def shouldPush(push: String): Boolean = {
    logger.trace(s"SPDY url '$push' being requested")

    // Get our map or wait until the last process is done with it
    var map = ref.getAndSet(null)
    while (map == null) map = ref.getAndSet(null)

    val result = map.put(push, None) == null   // Just put any old object in there

    // set the map back for the next use
    ref.set(map)

    result
  }
}
