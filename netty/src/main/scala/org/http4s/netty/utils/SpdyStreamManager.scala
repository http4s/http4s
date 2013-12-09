package org.http4s.netty.utils

import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.ConcurrentHashMap
import org.http4s.netty.spdy.SpdyStream

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */

/** Manages the stream ID's for the SPDY protocol
  * In a separate trait to clean up the SpdyNettyHandler class
  */
trait SpdyStreamManager { self: Logging =>
  /** Serves as a repository for active streams
    * If a stream is canceled, it get removed from the map. The allows the client to reject
    * data that it knows is already cached and this backend abort the outgoing stream
    */
  private val _managerRunningStreams = new ConcurrentHashMap[Int, SpdyStream]
  private val _managerCurrentStreamID = new AtomicInteger(0)
  private var _managerMaxStreams = Integer.MAX_VALUE    // 2^31
  private var _managerInitialStreamSize = 64*1024       // 64KB

  /** Whether this is a server or client
    *
    * @return if this is a server manager
    */
  def isServer: Boolean

  /** Version of SPDY employed */
  def spdyversion: Int

  /** Number of registered streams
    *
    * @return count of registered streams
    */
  def activeStreams(): Int = _managerRunningStreams.size()

  /** Clear all active streams */
  def clearStreams() {
    foreachStream(_.close())
    _managerRunningStreams.clear()
  }

  def killStreams(cause: Throwable) {
    foreachStream(_.kill(cause))
    _managerRunningStreams.clear()
  }

  /** Stream is finished and can be removed
    *
    * @param id streamid to be removed
    */
  def streamFinished(id: Int): Option[SpdyStream] =  {
    logger.trace(s"Stream $id finished. Closing.")
    Option(_managerRunningStreams.remove(id))
  }

  /** Tell about the current request stream ID and set it if it is the new highest stream ID
    * @param id ID if the SynStream request
    */
  def setCurrentStreamID(id: Int) {
    def go(): Unit = {
      val current = _managerCurrentStreamID.get()
      if (id > current) {  // Valid ID
        if(!_managerCurrentStreamID.compareAndSet(current, id)) go()
      }
      else logger.warn(s"StreamID $id is less than the current: $current")
    }
    go()
  }

  def getStream(id: Int): SpdyStream = _managerRunningStreams.get(id)

  def foreachStream(f: SpdyStream => Any) {
    val it = _managerRunningStreams.values().iterator()
    while(it.hasNext) f(it.next())
  }

  /** Get the ID of the latest known SPDY stream
    * @return the stream ID
    */
  def lastOpenedStream: Int = _managerCurrentStreamID.get()

  /** Set the maximum number of streams
    *
    * @param n the requested maximum number of streams
    * @return the set maximum number of streams
    */
  def setMaxStreams(n: Int): Int = {
    if (n < _managerMaxStreams) _managerMaxStreams = n
    _managerMaxStreams
  }

  /** The initial size of SPDY stream windows
    *
    * @return the initial window size
    */
  def initialWindow: Int = _managerInitialStreamSize

  /** Set the initial window size of the SPDY stream
    * @param n size in bytes of the initial window
    */
  protected def setInitialWindow(n: Int): Unit = {
    _managerInitialStreamSize = n
  }

  /** Add the stream to the active streams
    *
    * @param stream stream to add
    * @return any stream that already existed with the same stream ID
    */
  def putStream(stream: SpdyStream): Option[SpdyStream] = {
    Option(_managerRunningStreams.put(stream.streamid, stream))
  }

  /** Make the stream by generating a new streamID
    *
    * @param f method to make the stream once given an id
    * @return the created stream, if adding the stream was successful
    */
  def makeStream(f: Int => SpdyStream): Option[SpdyStream] = {
    val id = newStreamID()
    if (id > 0) {
      val newStream = f(id)
      val old = _managerRunningStreams.put(id, newStream)
      if (old == null) Some(newStream)
      else {
        _managerRunningStreams.put(id, old)  // Put it back
        None
      }
    }
    else None
  }

  /** Create a new stream ID for creating a new SPDY stream
    * @return ID to assign to the stream
    */
  private def newStreamID(): Int = {  // Need an odd integer
    if (activeStreams() + 1 > _managerMaxStreams) return -1

    val modulo = if (isServer) 0 else 1
    def go(): Int = {
      val current = _managerCurrentStreamID.get()
      val inc = if (current % 2 == modulo) 2 else 1
      val next = current + inc
      if (!_managerCurrentStreamID.compareAndSet(current, next)) go()
      // Make sure we don't pass the maximum number of streams
      else if (next < Integer.MAX_VALUE) next else -1
    }
    go()
  }
}
