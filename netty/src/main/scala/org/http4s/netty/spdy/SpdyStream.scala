package org.http4s.netty.spdy

import scalaz.concurrent.Task
import io.netty.handler.codec.spdy._
import com.typesafe.scalalogging.slf4j.Logging

import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import org.http4s.Response
import org.http4s.TrailerChunk
import org.http4s.Header.`Content-Length`
import org.http4s.util.middleware.PushSupport.PushResponse
import io.netty.handler.codec.http.HttpResponseStatus

import org.http4s.netty.utils.SpdyConstants._
import org.http4s.netty.utils.SpdyStreamManager

/**
* @author Bryce Anderson
*         Created on 12/4/13
*/
trait SpdyStream extends SpdyStreamOutput { self: Logging =>

  type Parent <: SpdyOutboundWindow with SpdyStreamManager
  
  private var _streamIsOpen = true

  protected def parent: Parent

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

  protected def canceledFuture(): ChannelFuture = {
    val p = ctx.newPromise()
    p.cancel(true)
    p
  }

  protected def writeBodyBytes(buff: ByteBuf): ChannelFuture = {
    if (_streamIsOpen) parent.writeStreamBuffer(streamid, buff)
    else canceledFuture()
  }

  protected def writeEndBytes(buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture = {
    if (_streamIsOpen) parent.writeStreamEnd(streamid, buff, t)
    else canceledFuture()
  }

  private def getStatus(response: Response) =
    new HttpResponseStatus(response.status.code, response.status.reason)
}
