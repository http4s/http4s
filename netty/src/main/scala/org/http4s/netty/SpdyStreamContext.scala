package org.http4s
package netty

import io.netty.handler.codec.spdy._
import scalaz.concurrent.Task
import org.http4s.netty.utils.{ClosedChunkHandler, ChunkHandler}
import io.netty.channel.ChannelHandlerContext
import io.netty.buffer.Unpooled
import com.typesafe.scalalogging.slf4j.Logging
import io.netty.handler.codec.http.HttpVersion._

import scalaz.{-\/, \/-}
import scalaz.stream.Process.{End, halt, Process1, await, Get}

import org.http4s.PushSupport.PushResponse
import org.http4s.Header.`Content-Length`
import io.netty.handler.codec.http.HttpResponseStatus

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */

class SpdyStreamContext(parentHandler: SpdyNettyHandler, val streamid: Int, val manager: ChunkHandler) extends Logging {

  private def spdyversion = parentHandler.spdyversion     // Shortcut

  private var isalive = true

  def spdyMessage(msg: SpdyFrame): Unit = msg match {     // the SpdyNettyHandler forwards messages to this method

    case chunk: SpdyDataFrame =>
      manager.enque(parentHandler.buffToBodyChunk( chunk.content()))
      if (chunk.isLast) manager.close()

    case headers: SpdyHeadersFrame =>
      logger.error("Header frames not supported yet. Dropping.")
      if (headers.isLast) {
        if (!headers.headers().isEmpty) {
          val headercollection = parentHandler.toHeaders(headers.headers)
          manager.close(TrailerChunk(headercollection))
        }
        else manager.close()
      }

    case rst: SpdyRstStreamFrame => close()


  }

  // TODO: implement these
  def close(): Task[Unit] = {
    isalive = false
    Task.now()
  }

  def kill(t: Throwable): Task[Unit] = {
    isalive = false
    Task.now()
  }

  def renderResponse(ctx: ChannelHandlerContext, req: SpdySynStreamFrame, response: Response): Task[Int] = {
    logger.trace("Rendering response.")
    val resp = new DefaultSpdySynReplyFrame(streamid)
    val size = copyResponse(resp, response)

    val t: Task[Any] = response.attributes.get(PushSupport.pushResponsesKey) match {
      case None => Task.now(())

      case Some(t) => // Push the heads of all the push resources. Sync on the Task
        t.flatMap (_.foldLeft(Task.now((streamid)))( (t, r) => t.flatMap( _ => pushResource(r, req, ctx))))
    }

    t.flatMap { _ =>
      ctx.channel.write(resp)
      renderBody(response.body, ctx, size <= 0)
    }
  }

  private def pushResource(push: PushResponse, req: SpdySynStreamFrame, ctx: ChannelHandlerContext): Task[Int] = {

    val id = parentHandler.newPushStreamID()
    val parentid = req.getStreamId
    val host = SpdyHeaders.getHost(req)
    val scheme = SpdyHeaders.getScheme(parentHandler.spdyversion, req)
    val response = push.resp
    logger.trace(s"Pushing content on stream $id associated with stream $parentid, url ${push.location}")

    val pushedctx = new SpdyStreamContext(parentHandler, id, new ClosedChunkHandler)
    assert(parentHandler.registerStream(pushedctx)) // Add a dummy Handler to signal that the stream is active

    // TODO: Default to priority 2. Fix this?
    val msg = new DefaultSpdySynStreamFrame(id, parentid, 2.toByte)
    SpdyHeaders.setUrl(spdyversion, msg, push.location)
    SpdyHeaders.setScheme(spdyversion, msg, scheme)
    SpdyHeaders.setHost(msg, host)
    val size = copyResponse(msg, response)

    ctx.channel().write(msg)
    pushedctx.renderBody(response.body, ctx, size <= 0)
  }

  // TODO: need to honor Window size messages to maintain flow control
  // TODO: need to make this lazy on flushes so we don't end up overflowing the netty outbound buffers
  private def renderBody(body: HttpBody, ctx: ChannelHandlerContext, flush: Boolean): Task[Int] = {
    logger.trace(s"Rendering SPDY body on stream $streamid")
    // Should have a ChunkHandler ready for us, start pushing stuff through either a Process1 or a sink
    val channel = ctx.channel()
    var stillOpen = true
    def folder(chunk: Chunk): Process1[Chunk, Unit] = {

      // Make sure our stream is still open, if not, halt.
      if (isalive) chunk match {
        case c: BodyChunk =>
          val out = new DefaultSpdyDataFrame(streamid, Unpooled.wrappedBuffer(chunk.toArray))
          if (flush) channel.writeAndFlush(out)
          else channel.write(out)
          await(Get[Chunk])(folder)

        case c: TrailerChunk =>
          val respTrailer = new DefaultSpdyHeadersFrame(streamid)
          for ( h <- c.headers ) respTrailer.headers().set(h.name.toString, h.value)
          respTrailer.setLast(true)
          stillOpen = false
          channel.writeAndFlush(respTrailer)
          halt
      }
      else {
        logger.trace(s"Stream $streamid canceled. Dropping rest of frames.")
        halt
      }  // Stream has been closed
    }

    val process1: Process1[Chunk, Unit] = await(Get[Chunk])(folder)

    body.pipe(process1).run.map { _ =>
      logger.trace(s"Finished stream $streamid")
      if (stillOpen) {
        val closer = new DefaultSpdyDataFrame(streamid)
        closer.setLast(true)
        channel.writeAndFlush(closer)
      }
      streamid
    }
  }

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

  private def getStatus(response: Response) = {
    new HttpResponseStatus(response.prelude.status.code, response.prelude.status.reason)
  }

}
