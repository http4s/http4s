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

/**
* @author Bryce Anderson
*         Created on 12/4/13
*/
trait SpdyStream extends SpdyStreamOutput { self: Logging =>

  protected def parent: SpdyNettyHandler

  def close(): Task[Unit]

  def handleStreamFrame(msg: SpdyStreamFrame): Unit

  def streamid: Int

  def spdyversion: Int = parent.spdyversion  // TODO: this is temporary

  def kill(t: Throwable): Task[Unit] = {
    closeSpdyWindow()
    close()
  }

  def handle(msg: SpdyFrame): Unit = msg match {
    case msg: SpdyStreamFrame => handleStreamFrame(msg)

    case msg: SpdyWindowUpdateFrame =>
      assert(msg.getStreamId == streamid)
      updateWindow(msg.getDeltaWindowSize)
  }

  /** Submits the head of the resource, and returns the execution of the submission of the body as a Task */
  protected def pushResource(push: PushResponse, req: SpdySynStreamFrame): Task[Unit] = {

    val id = parent.newPushStreamID()
    val parentid = req.getStreamId
    val host = SpdyHeaders.getHost(req)
    val scheme = SpdyHeaders.getScheme(parent.spdyversion, req)
    val response = push.resp
    logger.trace(s"Pushing content on stream $id associated with stream $parentid, url ${push.location}")

    val pushedStream = new SpdyPushStream(id, ctx, parent, initialWindow)
    assert(parent.registerStream(pushedStream)) // Add a dummy Handler to signal that the stream is active

    // TODO: Default to priority 2. What should we really have?
    val msg = new DefaultSpdySynStreamFrame(id, parentid, 2.toByte)
    SpdyHeaders.setUrl(spdyversion, msg, push.location)
    SpdyHeaders.setScheme(spdyversion, msg, scheme)
    SpdyHeaders.setHost(msg, host)
    copyResponse(msg, response)

    ctx.write(msg)

    // Differ writing of the body until the main resource can go as well. This might be cached or unneeded
    Task.suspend(pushedStream.writeStream(response.body).flatMap(_ => pushedStream.close() ))
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

  protected def writeBodyBytes(buff: ByteBuf): ChannelFuture = {
    parent.writeStreamBuffer(streamid, buff)
  }

  protected def writeEndBytes(buff: ByteBuf, t: Option[TrailerChunk]): ChannelFuture = {
    parent.writeStreamEnd(streamid, buff, t)
  }

  private def getStatus(response: Response) =
    new HttpResponseStatus(response.status.code, response.status.reason)
}
