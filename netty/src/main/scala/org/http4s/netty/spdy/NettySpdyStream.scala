package org.http4s.netty.spdy

import scalaz.concurrent.Task
import com.typesafe.scalalogging.slf4j.Logging

import org.http4s.Response
import org.http4s.TrailerChunk
import org.http4s.Header.`Content-Length`
import org.http4s.util.middleware.PushSupport.PushResponse

import org.http4s.netty.utils.SpdyConstants._
import org.http4s.netty.utils.SpdyStreamManager
import scala.concurrent.Future
import io.netty.handler.codec.spdy._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.http4s.Response
import io.netty.handler.codec.http.HttpResponseStatus
import org.http4s.netty.ProcessWriter
import io.netty.channel.ChannelHandlerContext

/**
* @author Bryce Anderson
*         Created on 12/4/13
*/
trait NettySpdyStream extends SpdyStream { self: Logging =>

  type Repr = NettySpdyStream

  type Parent = NettySpdyServerHandler
  
  private var _streamIsOpen = true

  protected def parent: NettySpdyServerHandler

  def ec = parent.ec

  protected def ctx: ChannelHandlerContext

  def handleStreamFrame(msg: SpdyStreamFrame): Unit

  def streamid: Int

  def spdyversion: Int = parent.spdyversion  // TODO: this is temporary

  def close(): Task[Unit] = {
    _streamIsOpen = false
    closeSpdyOutboundWindow()
    parent.streamFinished(streamid)
    Task.now()
  }

  def kill(t: Throwable): Task[Unit] = {
    close()
  }

  def handleRstFrame(msg: SpdyRstStreamFrame) = msg.getStatus match {
    case i if i == REFUSED_STREAM  || i == CANCEL => close()
    case i => kill(new Exception(s"Push stream $streamid received RST frame with code $i"))
  }

  def handle(msg: SpdyFrame): Unit = msg match {
    case msg: SpdyStreamFrame => handleStreamFrame(msg)

    case msg: SpdyWindowUpdateFrame =>
      assert(msg.getStreamId == streamid)
      updateOutboundWindow(msg.getDeltaWindowSize)
  }

  protected def copyResponse(spdyresp: SpdyHeadersFrame, response: Response): Int = {
    SpdyHeaders.setStatus(parent.spdyversion, spdyresp, getStatus(response))
    SpdyHeaders.setVersion(spdyversion, spdyresp, HTTP_1_1)

    var size = -1
    response.headers.foreach { header =>
      if (header.is(`Content-Length`)) size = header.parsed.asInstanceOf[`Content-Length`].length
      spdyresp.headers.set(header.name.toString, header.value)
    }
    size
  }

  private def getStatus(response: Response) =
    new HttpResponseStatus(response.status.code, response.status.reason)
}
