package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging
import io.netty.handler.codec.spdy.{DefaultSpdySynReplyFrame, SpdySynStreamFrame, SpdyStreamFrame}
import scalaz.concurrent.Task

import org.http4s.Response
import io.netty.channel.ChannelHandlerContext
import org.http4s.netty.utils.ChunkHandler
import org.http4s.util.middleware.PushSupport

/**
 * @author Bryce Anderson
 *         Created on 12/5/13
 */
class SpdyReplyStream(val streamid: Int,
                      protected val ctx: ChannelHandlerContext,
                      protected val parent: SpdyNettyHandler,
                      val initialWindow: Int) extends SpdyStream with Logging {

  val chunkHandler = new ChunkHandler(initialWindow) {

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
    parent.streamFinished(streamid)
    Task.now(())
  }

  def handleStreamFrame(msg: SpdyStreamFrame): Unit = {
    logger.trace(s"Stream $streamid received SpdyStreamFrame: $msg")
  }
}
