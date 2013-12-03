package org.http4s.netty.utils

import io.netty.handler.codec.spdy.DefaultSpdyRstStreamFrame

/**
 * @author Bryce Anderson
 *         Created on 12/1/13
 */
object SpdyConstants {
  val SPDY_MAX_LENGTH = 0xFFFFFF

  val PROTOCOL_ERROR = 1
  val INVALID_STREAM = 2
  val REFUSED_STREAM = 3
  val UNSUPPORTED_VERSION = 4
  val CANCEL = 5
  val INTERNAL_ERROR = 6
  val FLOW_CONTROL_ERROR = 7
  val STREAM_IN_USE = 8
  val STREAM_ALREADY_CLOSED = 9
  val FRAME_TOO_LARGE = 11

  def FLOW_CONTROL_ERROR(streamid: Int) = new DefaultSpdyRstStreamFrame(streamid, 7)

}
