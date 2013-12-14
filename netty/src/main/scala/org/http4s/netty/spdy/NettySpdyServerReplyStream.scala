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

/** Representation of a Server reply to a SPDY request
  * 
  * @param streamid this streams id
  * @param ctx ChannelHandlerContext
  * @param parent SpdyNettyHandler with which to route messages back too
  */
final class NettySpdyServerReplyStream(val streamid: Int,
                      protected val ctx: ChannelHandlerContext,
                      protected val parent: NettySpdyServerHandler)
                extends NettySpdyServerStream
                with SpdyTwoWayStream
                with Logging {

  //////   SpdyInboundWindow methods   ////////////////////////////////

  override protected def submitDeltaInboundWindow(n: Int): Unit = {
    ctx.writeAndFlush(new DefaultSpdyWindowUpdateFrame(streamid, n))
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
      val t = writeProcess(response.body).flatMap(_ => close() )

      if (queueSize() > 0)   // If we didn't use the bytes, account for them in parent window
        parent.incrementWindow(queueSize())

      Task.gatherUnordered(pushes :+ t, true)
    }
  }

  def handleStreamFrame(msg: SpdyStreamFrame): Unit = msg match {

    case msg: SpdyDataFrame =>
      val len = msg.content().readableBytes()
      decrementWindow(len)

    // Don't increment the stream window, don't want any more bytes
      if (enqueue(buffToBodyChunk(msg.content)) == -1)
        parent.incrementWindow(len)

    case msg: SpdyHeadersFrame => closeInbound(TrailerChunk(toHeaders(msg.headers)))
    case msg: SpdyRstStreamFrame => handleRstFrame(msg)

      // TODO: handle more types of messages correctly
    case msg => logger.trace(s"Stream $streamid received SpdyStreamFrame: $msg")
  }
}
