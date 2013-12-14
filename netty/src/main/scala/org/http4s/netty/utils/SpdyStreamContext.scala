package org.http4s.netty.utils

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.slf4j.Logging

import org.http4s.netty.spdy.{SpdyInboundWindow, SpdyConnectionOutboundWindow, SpdyStream}
import org.http4s.netty.NettySupport.InvalidStateException
import org.http4s.netty.utils.SpdyStreamContext.{StreamIndexException, MaxStreamsException}

import scala.util.{Failure, Success, Try}

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */

/** Manages the stream ID's for the SPDY protocol
  * In a separate trait to clean up the SpdyNettyHandler class
  */
abstract class SpdyStreamContext[S <: SpdyStream](val spdyversion: Int, val isServer: Boolean)
                  extends SpdyInboundWindow
                  with SpdyConnectionOutboundWindow
                  with Logging {
  /** Serves as a repository for active streams
    * If a stream is canceled, it get removed from the map. The allows the client to reject
    * data that it knows is already cached and this backend abort the outgoing stream
    */
  private val _managerRunningStreams = new ConcurrentHashMap[Int, S]
  private val _managerCurrentStreamID = new AtomicInteger(0)
  private var _managerMaxStreams = Integer.MAX_VALUE    // 2^31
  private var _managerInitialOutboundStreamSize = 64*1024       // 64KB
  private var _managerInitialInboundStreamSize =  64*1024

  protected def manager: this.type = this

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
  def streamFinished(id: Int): Option[S] =  {
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

  def getStream(id: Int): S = _managerRunningStreams.get(id)

  def foreachStream(f: S => Any) {
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

  /** The initial size of SPDY outbound stream windows
    *
    * @return the initial window size
    */
  def initialOutboundWindow: Int = _managerInitialOutboundStreamSize

  /** The initial size of SPDY inbound stream windows
    *
    * @return the initial window size
    */
  def initialInboundWindow: Int = _managerInitialInboundStreamSize

  /** Set the initial window size of the SPDY stream
    * @param n size in bytes of the initial window
    */
  def setInitialOutboundWindow(n: Int): Unit = {
    _managerInitialOutboundStreamSize = n
  }

  def setInitialInboundWindow(n: Int): Unit = {
    _managerInitialInboundStreamSize = n
  }

  /** Add the stream to the active streams
    *
    * @param stream stream to add
    * @return true if the stream was placed successfully, otherwise false
    */
  def putStream(stream: S): Boolean = {

    // Make sure we are indexing streams right
    val current = _managerCurrentStreamID.get()
    if (current > stream.streamid)
      _managerCurrentStreamID.compareAndSet(current, stream.streamid)

    // Add the stream
    val old = _managerRunningStreams.put(stream.streamid, stream)
    if (old == null) true
    else { // Put it back and return false
      _managerRunningStreams.put(old.streamid, old)
      false
    }
  }

  /** Make the stream by generating a new streamID
    *
    * @param f method to make the stream once given an id
    * @return the created stream, if adding the stream was successful
    */
  def makeStream(f: Int => S): Try[S] = {

    if (activeStreams() + 1 > _managerMaxStreams)
      return Failure(MaxStreamsException(_managerMaxStreams))

    val id = newStreamID()
    if (id > 0) {
      val newStream = f(id)
      val old = _managerRunningStreams.put(id, newStream)
      if (old == null) Success(newStream)
      else {
        _managerRunningStreams.put(id, old)  // Put it back
        Failure(new InvalidStateException(s"Generated an invalid stream id: $id"))
      }
    }
    else Failure(StreamIndexException())
  }

  /** Create a new stream ID for creating a new SPDY stream
    * @return ID to assign to the stream
    */
  private def newStreamID(): Int = {  // Need an odd integer
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

object SpdyStreamContext {
  case class MaxStreamsException(maxStreams: Int)
          extends InvalidStateException(s"Maximum streams exceeded: $maxStreams")

  case class StreamExistsException(id: Int)
          extends InvalidStateException(s"Stream with id $id already exists.")

  case class StreamIndexException()
          extends InvalidStateException(s"Streams reached maximum index of ${Integer.MAX_VALUE}")
}

