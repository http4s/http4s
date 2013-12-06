package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.spdy.{SpdyHeadersFrame, SpdyStreamFrame}
import scalaz.concurrent.Task

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
class SpdyPushStream(val streamid: Int,
                     protected val ctx: ChannelHandlerContext,
                     protected val parent: SpdyNettyHandler,
                     val initialWindow: Int) extends SpdyStream with Logging {


  def close(): Task[Unit] = {
    parent.streamFinished(streamid)
    Task.now()
  }

  def handleStreamFrame(msg: SpdyStreamFrame): Unit = sys.error("Push Stream doesn't operate on requests")
}
