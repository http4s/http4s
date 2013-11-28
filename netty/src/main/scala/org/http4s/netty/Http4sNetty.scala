package org.http4s
package netty

import scala.util.control.Exception.allCatch

import java.net.{InetSocketAddress, URI, InetAddress}

import io.netty.handler.ssl.SslHandler

import io.netty.handler.codec.http
import http.HttpHeaders.{Names, Values, isKeepAlive}

import org.http4s.{Header, Chunk, Response, RequestPrelude}
import org.http4s.TrailerChunk

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.channel.{ChannelOption, ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.util.{ReferenceCountUtil, CharsetUtil}
import io.netty.buffer.{Unpooled, ByteBuf}
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.http.{HttpObject, DefaultHttpContent}
import scala.collection.mutable.ListBuffer
import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}
import scalaz.stream.Process._
import scalaz.stream.async
import java.nio.channels.ServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.lang.Boolean
import java.util
import java.io.IOException


object Http4sNetty {
  def apply(toMount: HttpService) =
    new Http4sNetty {
      val service = toMount
    }
}

abstract class Http4sNetty extends SimpleChannelInboundHandler[HttpObject] with Logging {

  def service: HttpService

  val serverSoftware = ServerSoftware("HTTP4S / Netty")

  private var manager: ChannelManager = null

  class ChannelManager(ctx: ChannelHandlerContext) extends ChunkHandler(10, 5) {
    def onQueueFull() {
      logger.trace("Queue full.")
      assert(ctx != null)
      disableRead(ctx)
    }

    def onQueueReady() {
      logger.trace("Queue ready.")
      assert(ctx != null)
      enableRead(ctx)
    }
  }


  private def disableRead(ctx: ChannelHandlerContext) {
    ctx.channel().config().setOption(ChannelOption.AUTO_READ, new Boolean(false))
  }

  private def enableRead(ctx: ChannelHandlerContext) {
    ctx.channel().config().setOption(ChannelOption.AUTO_READ, new Boolean(true))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    // Kill the Stream
    if (manager != null) manager.kill(cause)

    cause match {
      case ex: IOException =>
        ctx.channel().close()
        return
    }

    logger.error("Caught exception: %s", cause.getStackTraceString)
    println(s"Caught an exception: ${cause.getMessage}, by ${cause.getClass}\n" + cause.getStackTraceString)
    try {
      if (ctx.channel.isOpen) {
        val msg = (cause.getMessage + "\n" + cause.getStackTraceString).getBytes(CharsetUtil.UTF_8)
        val buff = Unpooled.wrappedBuffer(msg)
        val resp = new http.DefaultFullHttpResponse(http.HttpVersion.HTTP_1_0, http.HttpResponseStatus.INTERNAL_SERVER_ERROR, buff)

        ctx.channel.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
      }
    } catch {
      case t: Throwable =>
        logger.error(t.getMessage, t)
        allCatch(ctx.close())
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = msg match {
    case req: http.HttpRequest =>
      logger.trace("Netty request received")
      assert(manager == null)
      startHttpRequest(ctx, req, ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)

    case c: http.LastHttpContent =>
      if (manager != null) {
        if (c.content().readableBytes() > 0)
          manager.enque(buffToBodyChunk(c.content))

        manager.close(TrailerChunk(toHeaders(c.trailingHeaders())))
        manager = null
      } else logger.warn("Received LastHttpContent but manager is null. Discarding.")

    case chunk: http.HttpContent =>
      logger.trace("Netty content received.")
      if (manager != null) manager.enque(buffToBodyChunk(chunk.content))
      else logger.warn("Received HttpContent but manager is null. Discarding.")

    case msg => forward(ctx, msg)// Done know what it is...
  }

  private def forward(ctx: ChannelHandlerContext, msg: AnyRef) {
    ReferenceCountUtil.retain(msg)    // We will be releasing this ref, so need to inc to keep consistent
    ctx.fireChannelRead(msg)
  }

  private def stateError(o: AnyRef): Nothing = sys.error("Received invalid combination of chunks and stand parts. " + o.getClass)

  private def buffToBodyChunk(buff: ByteBuf) = {
    val arr = new Array[Byte](buff.readableBytes())
    buff.getBytes(0, arr)
    BodyChunk(arr)
  }

  private def startHttpRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, rem: InetAddress) {
    logger.trace("Starting http request.")
    if (manager != null) stateError(req)

    val request = toRequest(ctx, req, rem)
    val task = try service(request).flatMap(renderResponse(ctx, req, _))
    catch {
      // TODO: don't rely on exceptions for bad requests
      case m: MatchError => Status.NotFound(request.prelude)
      case e: Throwable => throw e
    }
    task.runAsync {
      case -\/(t) =>
      logger.error("Final Task results in an Exception.", t)
        ctx.channel().close()

      // Make sure we are allowing reading. Might be disabled of there was unused body
      case \/-(_) =>  if (ctx.channel.isOpen) enableRead(ctx)
    }

  }

  protected def renderResponse(ctx: ChannelHandlerContext, req: http.HttpRequest, response: Response): Task[Unit] = {
    val prelude = response.prelude
    val stat = new http.HttpResponseStatus(prelude.status.code, prelude.status.reason)

    val length = prelude.headers.get(Header.`Content-Length`).map(_.length)
    val isHttp10 = req.getProtocolVersion == http.HttpVersion.HTTP_1_0

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

    if(!isHttp10 && length.isEmpty) headers += ((http.HttpHeaders.Names.TRANSFER_ENCODING, http.HttpHeaders.Values.CHUNKED))

    val msg = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
    headers.foreach { case (k, v) => msg.headers.set(k,v) }
    response.prelude.headers.foreach(h => msg.headers().set(h.name.toString, h.value))
    if (length.isEmpty) ctx.channel.writeAndFlush(msg)
    else ctx.channel.write(msg)

    var closed = false

    def folder(chunk: Chunk): Process1[Chunk, Unit] = {
      chunk match {
        case c: BodyChunk =>
          val out = new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray))
          if (length.isEmpty) ctx.channel().writeAndFlush(out)
          else ctx.channel().write(out)
          await(Get[Chunk])(folder)

        case c: TrailerChunk =>
          val respTrailer = new http.DefaultLastHttpContent()
          for ( h <- c.headers ) respTrailer.trailingHeaders().set(h.name.toString, h.value)

          val f = ctx.channel.writeAndFlush(respTrailer)
          if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
          closed = true
          halt
      }
    }

    val process1: Process1[Chunk, Unit] = await(Get[Chunk])(folder)
    response.body.pipe(process1).run.map { _ =>
      logger.trace("Finished stream.")
      if (!closed && ctx.channel.isOpen) {
          val msg = new http.DefaultLastHttpContent()
          val f = ctx.channel.writeAndFlush(msg)
          if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }

  private def toRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, remote: InetAddress): Request = {
    val scheme = if (ctx.pipeline.get(classOf[SslHandler]) != null) "http" else "https"
    logger.trace("Received request: " + req.getUri)
    val uri = new URI(req.getUri)

    val servAddr = ctx.channel.remoteAddress.asInstanceOf[InetSocketAddress]
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
      remote = remote // TODO using remoteName would trigger a lookup
    )

    manager = new ChannelManager(ctx)

    val cleanup = eval(Task{ if (manager != null) manager.kill(End); throw End})
    val t = Task.async[Chunk](cb => manager.request(cb))
    val stream = await(t)(emit, cleanup = cleanup).repeat

    Request(prelude, stream)
  }

  private def toHeaders(headers: http.HttpHeaders) = {
    import collection.JavaConversions._
    HeaderCollection( headers.entries.map(entry => Header(entry.getKey, entry.getValue)):_*)
  }
}

