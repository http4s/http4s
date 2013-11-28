package org.http4s
package netty

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.handler.codec.http._
import io.netty.handler.codec.http.HttpHeaders._
import io.netty.channel.{ChannelOption, ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.ssl.SslHandler
import io.netty.buffer.Unpooled

import scalaz.concurrent.Task
import scalaz.stream.Process
import Process._

import scala.collection.mutable.ListBuffer
import java.net.{InetSocketAddress, URI, InetAddress}
import org.http4s.Request
import org.http4s.RequestPrelude
import org.http4s.TrailerChunk


/**
 * @author Bryce Anderson
 *         Created on 11/28/13
 */
abstract class HttpNettySupport extends NettySupport[HttpObject] {

  type RequestType = HttpRequest

  private var manager: ChannelManager = null

  protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, response: Response): Task[Unit] = {

    val prelude = response.prelude
    val stat = new HttpResponseStatus(prelude.status.code, prelude.status.reason)

    val length = prelude.headers.get(Header.`Content-Length`).map(_.length)
    val isHttp10 = req.getProtocolVersion == HttpVersion.HTTP_1_0

    val headers = new ListBuffer[(String, String)]

    val closeOnFinish = if (isHttp10) {
      if (length.isEmpty && isKeepAlive(req)) {
        headers += ((Names.CONNECTION, Values.CLOSE))
        true
      } else if(isKeepAlive(req)) {
        headers += ((Names.CONNECTION, Values.KEEP_ALIVE))
        false
      } else true
    } else if (Values.CLOSE.equalsIgnoreCase(req.headers.get(Names.CONNECTION))){  // Http 1.1+
      headers += ((Names.CONNECTION, Values.CLOSE))
      true
    } else false

    if(!isHttp10 && length.isEmpty) headers += ((Names.TRANSFER_ENCODING, Values.CHUNKED))

    val msg = new DefaultHttpResponse(req.getProtocolVersion, stat)
    headers.foreach { case (k, v) => msg.headers.set(k,v) }
    response.prelude.headers.foreach(h => msg.headers().set(h.name.toString, h.value))
    if (length.isEmpty) ctx.channel.writeAndFlush(msg)
    else ctx.channel.write(msg)

    sendOutput(response.body, length.isEmpty, closeOnFinish, ctx)
  }

  override def toRequest(ctx: ChannelHandlerContext, req: HttpRequest): Request = {
    val scheme = if (ctx.pipeline.get(classOf[SslHandler]) != null) "http" else "https"
    logger.trace("Received request: " + req.getUri)
    val uri = new URI(req.getUri)

    val servAddr = localAddress
    val prelude = RequestPrelude(
      requestMethod = Method(req.getMethod.name),
      //scriptName = contextPath,
      pathInfo = uri.getRawPath,
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.protocolName),
      headers = toHeaders(req.headers),
      urlScheme = HttpUrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = serverSoftware,
      remote = remoteAddress // TODO using remoteName would trigger a lookup
    )

    manager = new ChannelManager(ctx)

    val cleanup = eval(Task{
      if (manager != null) {
        manager.kill(End)
        manager = null
      }
      throw End
    })
    val t = Task.async[Chunk](cb => manager.request(cb))
    val stream = await(t)(emit, cleanup = cleanup).repeat

    Request(prelude, stream)
  }

  private def sendOutput(body: Process[Task, Chunk], flush: Boolean, closeOnFinish: Boolean, ctx: ChannelHandlerContext): Task[Unit] = {
    var closed = false
    def folder(chunk: Chunk): Process1[Chunk, Unit] = {
      chunk match {
        case c: BodyChunk =>
          val out = new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray))
          if (flush) ctx.channel().writeAndFlush(out)
          else ctx.channel().write(out)
          await(Get[Chunk])(folder)

        case c: TrailerChunk =>
          val respTrailer = new DefaultLastHttpContent()
          for ( h <- c.headers ) respTrailer.trailingHeaders().set(h.name.toString, h.value)

          val f = ctx.channel.writeAndFlush(respTrailer)
          if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
          closed = true
          halt
      }
    }

    val process1: Process1[Chunk, Unit] = await(Get[Chunk])(folder)

    body.pipe(process1).run.map { _ =>
      logger.trace("Finished stream.")
      if (!closed && ctx.channel.isOpen) {     // Deal with end of process that didn't give a Trailer
      val msg = new DefaultLastHttpContent()
        val f = ctx.channel.writeAndFlush(msg)
        if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }

  class ChannelManager(ctx: ChannelHandlerContext) extends ChunkHandler(10, 5) {
    def onQueueFull() {
      logger.trace("Queue full.")
      assert(ctx != null)
      disableRead()
    }

    def onQueueReady() {
      logger.trace("Queue ready.")
      assert(ctx != null)
      enableRead()
    }

    private def disableRead() {
      ctx.channel().config().setOption(ChannelOption.AUTO_READ, new java.lang.Boolean(false))
    }

    private def enableRead() {
      ctx.channel().config().setOption(ChannelOption.AUTO_READ, new java.lang.Boolean(true))
    }
  }
}
