package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging
import io.netty.handler.codec.spdy._
import scalaz.concurrent.Task

import org.http4s.{TrailerChunk, Chunk, Response}
import io.netty.channel.ChannelHandlerContext
import org.http4s.netty.utils.ChunkHandler
import org.http4s.util.middleware.PushSupport

import org.http4s.netty.NettySupport._

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */

/** Representation of a Server reply to a Spdy request
  * 
  * @param streamid this streams id
  * @param ctx ChannelHandlerContext
  * @param parent SpdyNettyHandler with which to route messages back too
  * @param initialOutboundWindow flow control window size
  */
class SpdyReplyStream(val streamid: Int,
                      protected val ctx: ChannelHandlerContext,
                      protected val parent: SpdyNettyHandler,
                      val initialOutboundWindow: Int)
          extends SpdyStream
          with Logging
          with SpdyInboundWindow {

  //////   SpdyInboundWindow methods   ////////////////////////////////

  def initialInboundWindowSize: Int = initialOutboundWindow

  def submitDeltaWindow(n: Int): Unit = {
    ctx.writeAndFlush(new DefaultSpdyWindowUpdateFrame(streamid, n))
  }

  val chunkHandler = new ChunkHandler(initialOutboundWindow) {
    
    override def enque(chunk: Chunk): Int = {
      decrementWindow(chunk.length)
      super.enque(chunk)
    }

    override def onBytesSent(n: Int): Unit = incrementWindow(n)
  }

  /////////////////////////////////////////////////////////////////////

  def handleRequest(req: SpdySynStreamFrame, response: Response): Task[List[_]] = {
    logger.trace("Rendering response.")
    val resp = new DefaultSpdySynReplyFrame(streamid)
    val size = copyResponse(resp, response)

    val t: Task[Seq[Task[_]]] = response.attributes.get(PushSupport.pushResponsesKey) match {
      case None => Task.now(Nil)

      case Some(t) => // Push the heads of all the push resources. Sync on the Task
        t.map (_.map( r =>  pushResource(r, req) ))
    }

    t.flatMap { pushes =>
      ctx.write(resp)
      val t = writeStream(response.body).flatMap(_ => close() )
      Task.gatherUnordered(pushes :+ t, true)
    }
  }

  def handleStreamFrame(msg: SpdyStreamFrame): Unit = msg match {

    case msg: SpdyDataFrame => chunkHandler.enque(buffToBodyChunk(msg.content))
    case msg: SpdyHeadersFrame => chunkHandler.close(TrailerChunk(toHeaders(msg.headers)))
    case msg: SpdyRstStreamFrame => handleRstFrame(msg)

      // TODO: handle more types of messages correctly
    case msg => logger.trace(s"Stream $streamid received SpdyStreamFrame: $msg")
  }
}
