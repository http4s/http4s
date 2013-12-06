package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging
import io.netty.handler.codec.spdy.{DefaultSpdyWindowUpdateFrame, DefaultSpdySynReplyFrame, SpdySynStreamFrame, SpdyStreamFrame}
import scalaz.concurrent.Task

import org.http4s.{Chunk, Response}
import io.netty.channel.ChannelHandlerContext
import org.http4s.netty.utils.ChunkHandler
import org.http4s.util.middleware.PushSupport
import java.util.concurrent.atomic.AtomicInteger

import org.http4s.netty.utils.SpdyConstants._

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
                      val initialOutboundWindow: Int) extends SpdyStream with Logging {

  // Inbound flow control logic
  private var inboundWindowSize = initialOutboundWindow
  
  val chunkHandler = new ChunkHandler(initialOutboundWindow) {
    
    // TODO: what about initial window size changes here?
    private val handledQueue = new AtomicInteger(0)
    
    override def enque(chunk: Chunk): Int = {
      val queuelength = queueSize()+chunk.length
      if (queuelength > inboundWindowSize) {
        ctx.writeAndFlush(FLOW_CONTROL_ERROR(streamid))
        kill(new Exception(s"Inbound messages overflowed stream window"))
        -1
      } else super.enque(chunk)
    }

    override def request(cb: CB): Int = {
      val requested = super.request(cb)
      val handled = handledQueue.addAndGet(requested)

      // If we have depleted half the window and its not already been done, send a window update
      if (handled > inboundWindowSize /2 && handledQueue.compareAndSet(handled, 0)) {
        ctx.writeAndFlush(new DefaultSpdyWindowUpdateFrame(streamid, handled))
      }

      requested
    }
  }

  def setInboundWindow(n: Int) {
    inboundWindowSize = n
  }

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

  // TODO: implement these in a more meaningful way
  def close(): Task[Unit] = {
    closeSpdyWindow()
    parent.streamFinished(streamid)
    Task.now(())
  }

  def handleStreamFrame(msg: SpdyStreamFrame): Unit = {
    logger.trace(s"Stream $streamid received SpdyStreamFrame: $msg")
  }
}
