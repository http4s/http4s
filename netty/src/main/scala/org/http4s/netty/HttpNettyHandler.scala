package org.http4s
package netty

import io.netty.handler.codec.http._
import io.netty.handler.codec.http.HttpHeaders._
import io.netty.channel.{ChannelOption, ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.ssl.SslHandler
import io.netty.buffer.Unpooled

import scalaz.concurrent.Task
import scalaz.stream.Process
import Process._

import scala.collection.mutable.ListBuffer
import java.net.{InetSocketAddress, URI}
import org.http4s.Request
import org.http4s.RequestPrelude
import org.http4s.TrailerChunk
import io.netty.util.ReferenceCountUtil
import org.http4s.netty.utils.ChunkHandler


/**
 * @author Bryce Anderson
 *         Created on 11/28/13
 */
class HttpNettyHandler(val service: HttpService, val localAddress: InetSocketAddress, val remoteAddress: InetSocketAddress)
            extends NettySupport[HttpObject, HttpRequest] {

  import NettySupport._

  private var manager: ChannelManager = null

  val serverSoftware = ServerSoftware("HTTP4S / Netty / HTTP")

  def onHttpMessage(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case req: HttpRequest =>
      logger.trace("Netty request received")
      runHttpRequest(ctx, req)

    case c: LastHttpContent =>
      if (manager != null) {
        if (c.content().readableBytes() > 0)
          manager.enque(buffToBodyChunk(c.content))

        manager.close(TrailerChunk(toHeaders(c.trailingHeaders())))
        manager = null
      }
      else logger.trace("Received LastHttpContent but manager is null. Discarding.")

    case chunk: HttpContent =>
      logger.trace("Netty content received.")
      if (manager != null) manager.enque(buffToBodyChunk(chunk.content))
      else logger.trace("Received HttpContent but manager is null. Discarding.")

    case msg =>
      ReferenceCountUtil.retain(msg)   // Done know what it is, fire upstream
      ctx.fireChannelRead(msg)
  }

  override protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, response: Response): Task[Unit] = {

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

    if(manager != null) invalidState(s"Chunk manager still present. Is a previous request still underway? $manager")

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
      remote = remoteAddress.getAddress
    )

    manager = new ChannelManager(ctx)
    Request(prelude, getStream(manager))
  }

  private def sendOutput(body: Process[Task, Chunk], flush: Boolean, closeOnFinish: Boolean, ctx: ChannelHandlerContext): Task[Unit] = {
    var finished = false
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
          finished = true
          halt
      }
    }

    val p: Process1[Chunk, Unit] = await(Get[Chunk])(folder)

    body.pipe(p).run.map { _ =>
      logger.trace("Finished stream.")
      if (!finished && ctx.channel.isOpen) {
        val f = ctx.channel.writeAndFlush(new DefaultLastHttpContent())
        if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
      }
      logger.trace("Finished stream.")
      manager = null
    }
  }

  /** Manages the input stream providing back pressure
    * @param ctx ChannelHandlerContext of the channel
    */          // TODO: allow control of buffer size and use bytes, not chunks as limit
  protected class ChannelManager(ctx: ChannelHandlerContext) extends ChunkHandler(10, 5) {
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
