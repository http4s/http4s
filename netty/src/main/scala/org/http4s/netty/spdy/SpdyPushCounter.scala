package org.http4s.netty.spdy

import java.util.LinkedHashMap
import java.util.Map.Entry

/**
 * @author Bryce Anderson
 *         Created on 12/29/13
 */
class SpdyPushCounter(maxSize: Int) {
  private val DUMMY = new AnyRef
  private val pushed = new LinkedHashMap[String, Object](maxSize/2) {
    override def removeEldestEntry(eldest: Entry[String, Object]) = this.size() > maxSize
  }

  def shouldPush(push: String): Boolean = push.synchronized {
    pushed.put(push, DUMMY) == null
  }
}
