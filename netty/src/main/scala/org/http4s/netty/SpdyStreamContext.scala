package org.http4s
package netty

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.handler.codec.spdy._
import io.netty.channel.ChannelHandlerContext
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http.HttpResponseStatus

import scalaz.concurrent.Task
import scalaz.stream.Process
import Process.{End, halt, Process1, await, Get}

import org.http4s.netty.utils.{SpdyConstants, NettyOutput, ClosedChunkHandler, ChunkHandler}
import org.http4s.PushSupport.PushResponse
import org.http4s.Header.`Content-Length`


/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */

class SpdyStreamContext(protected val ctx: ChannelHandlerContext, val parentHandler: SpdyNettyHandler, val streamid: Int)
  extends SpdyWindowManager with Logging {

  import NettySupport._

  private def spdyversion = parentHandler.spdyversion     // Shortcut

  def renderResponse(ctx: ChannelHandlerContext, req: SpdySynStreamFrame, response: Response): Task[List[_]] = {
    logger.trace("Rendering response.")
    val resp = new DefaultSpdySynReplyFrame(streamid)
    val size = copyResponse(resp, response)

    val t: Task[Seq[Task[_]]] = response.attributes.get(PushSupport.pushResponsesKey) match {
      case None => Task.now(Nil)

      case Some(t) => // Push the heads of all the push resources. Sync on the Task
        t.map (_.map { r =>  pushResource(r, req) })
    }

    t.flatMap { pushes =>
      ctx.write(resp)

      // TODO: need to honor Window size messages to maintain flow control
      val t = writeStream(response.body, ctx)
      Task.gatherUnordered(pushes :+ t, true)
    }
  }

  /** Submits the head of the resource, and returns the execution of the submission of the body as a Task */
  private def pushResource(push: PushResponse, req: SpdySynStreamFrame): Task[Unit] = {

    val id = parentHandler.newPushStreamID()
    val parentid = req.getStreamId
    val host = SpdyHeaders.getHost(req)
    val scheme = SpdyHeaders.getScheme(parentHandler.spdyversion, req)
    val response = push.resp
    logger.trace(s"Pushing content on stream $id associated with stream $parentid, url ${push.location}")

    val pushedStream = new SpdyStreamContext(ctx, parentHandler, id)
    assert(parentHandler.registerStream(pushedStream)) // Add a dummy Handler to signal that the stream is active

    // TODO: Default to priority 2. What should we really have?
    val msg = new DefaultSpdySynStreamFrame(id, parentid, 2.toByte)
    SpdyHeaders.setUrl(spdyversion, msg, push.location)
    SpdyHeaders.setScheme(spdyversion, msg, scheme)
    SpdyHeaders.setHost(msg, host)
    val size = copyResponse(msg, response)

    ctx.write(msg)

    // Differ writing of the body until the main resource can go as well. This might be cached or unneeded
    Task.suspend(pushedStream.writeStream(response.body, ctx))
  }

  ////////////////////// NettyOutput Methods //////////////

  final def bufferToMessage(buff: ByteBuf) = new DefaultSpdyDataFrame(streamid, buff)

  final def endOfStreamChunk(trailer: Option[TrailerChunk]) = trailer match {
    case Some(t) =>
      val respTrailer = new DefaultSpdyHeadersFrame(streamid)
      for ( h <- t.headers ) respTrailer.headers().set(h.name.toString, h.value)
      respTrailer.setLast(true)
      respTrailer

    case None =>
      val closer = new DefaultSpdyDataFrame(streamid)
      closer.setLast(true)
      closer
  }
  /////////////////////////////////////////////////////////

  private def copyResponse(spdyresp: SpdyHeadersFrame, response: Response): Int = {
    SpdyHeaders.setStatus(parentHandler.spdyversion, spdyresp, getStatus(response))
    SpdyHeaders.setVersion(spdyversion, spdyresp, HTTP_1_1)

    var size = -1
    response.prelude.headers.foreach { header =>
      if (header.is(`Content-Length`)) size = header.parsed.asInstanceOf[`Content-Length`].length
      spdyresp.headers.set(header.name.toString, header.value)
    }
    size
  }

  private def getStatus(response: Response) =
    new HttpResponseStatus(response.prelude.status.code, response.prelude.status.reason)
}
