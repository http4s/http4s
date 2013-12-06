package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.spdy.{SpdyRstStreamFrame, SpdyStreamFrame}
import scalaz.concurrent.Task
import org.http4s.netty.utils.SpdyConstants._

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
class SpdyPushStream(val streamid: Int,
                     protected val ctx: ChannelHandlerContext,
                     protected val parent: SpdyNettyHandler,
                     val initialOutboundWindow: Int) extends SpdyStream with Logging {


  def close(): Task[Unit] = {
    closeSpdyWindow()
    parent.streamFinished(streamid)
    Task.now()
  }

  def handleRstFrame(msg: SpdyRstStreamFrame) = msg.getStatus match {

    case i if i == REFUSED_STREAM  || i == CANCEL => close()

    case i => kill(new Exception(s"Push stream $streamid received RST frame with code $i"))
  }

  def handleStreamFrame(msg: SpdyStreamFrame): Unit = msg match {

    case msg: SpdyRstStreamFrame => handleRstFrame(msg)

    case msg => sys.error(s"Push Stream received invalid reply frame: $msg")
  }
}
