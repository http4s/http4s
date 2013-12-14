package org.http4s.netty.spdy

import com.typesafe.scalalogging.slf4j.Logging

import org.http4s.util.middleware.PushSupport.PushResponse
import org.http4s.netty.utils.SpdyStreamContext.{StreamIndexException, MaxStreamsException}

import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.spdy.{DefaultSpdyGoAwayFrame, DefaultSpdySynStreamFrame, SpdyHeaders, SpdySynStreamFrame}

import scalaz.concurrent.Task
import scala.util.{Failure, Success}


/**
 * @author Bryce Anderson
 *         Created on 12/8/13
 */
trait NettySpdyServerStream extends NettySpdyStream { self: Logging =>

  /** Submits the head of the resource, and returns the execution of the submission of the body as a Task */

  /** push resources to the client
    *
    * @param push pushed resource description
    * @param req request message that this will be pushed with
    * @return a Task which will send the pushed resources
    */
  protected def pushResource(push: PushResponse, req: SpdySynStreamFrame): Task[Unit] = {

    val parentid = req.getStreamId
    val host = SpdyHeaders.getHost(req)
    val scheme = SpdyHeaders.getScheme(manager.spdyversion, req)
    val response = push.resp

    manager.makeStream(id => new NettySpdyPushStream(id, ctx, parent, manager)) match {
      case Success(stream) =>
        logger.trace(s"Pushing content on stream ${stream.streamid} associated with stream $parentid, url ${push.location}")
        // TODO: Default to priority 2. What should we really have?
        val msg = new DefaultSpdySynStreamFrame(stream.streamid, parentid, 2.toByte)
        SpdyHeaders.setUrl(manager.spdyversion, msg, push.location)
        SpdyHeaders.setScheme(manager.spdyversion, msg, scheme)
        SpdyHeaders.setHost(msg, host)
        copyResponse(msg, response)
        ctx.write(msg)

        // Differ writing of the body until the main resource can go as well. This might be cached or unneeded
        Task.suspend(stream.writeProcess(response.body).flatMap(_ => stream.close() ))

      case Failure(StreamIndexException()) =>
        logger.warn("Exceeded maximum streams or maximum stream ID. Need to spool down connection.")
        ctx.writeAndFlush(new DefaultSpdyGoAwayFrame(manager.lastOpenedStream))
        return Task.now()

      case Failure(MaxStreamsException(n)) => // Too many resources opened, don't create a new one
        logger.warn(s"Maximum streams exceeded, $n, on connection ${ctx.channel().localAddress()}")
        Task.now()

      case Failure(t) =>
        logger.error("Error creating server push stream", t)
        ctx.writeAndFlush(new DefaultSpdyGoAwayFrame(manager.lastOpenedStream))
          .addListener(ChannelFutureListener.CLOSE)
        Task.fail(t)
    }
  }
}
