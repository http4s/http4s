package org.http4s
package netty

import scala.language.implicitConversions

import scala.concurrent.ExecutionContext
import scala.util.control.Exception.allCatch

import play.api.libs.iteratee._
import java.net.{InetSocketAddress, URI, InetAddress}


import io.netty.handler.ssl.SslHandler

import io.netty.handler.codec.http
import http.HttpHeaders.{Names, Values, isKeepAlive}

import org.http4s.{Headers, Chunk, Responder, RequestPrelude}
import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.TrailerChunk

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelInboundHandlerAdapter, ChannelHandlerContext}
import io.netty.util.{ReferenceCountUtil, CharsetUtil}
import io.netty.buffer.{Unpooled, ByteBuf}
import org.http4s.Headers.RawHeader
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.http.{HttpServerCodec, DefaultHttpContent, DefaultFullHttpResponse}
import scala.collection.mutable.ListBuffer
import akka.util.ByteString


object Http4sNetty {
  def apply(toMount: Route)(implicit executor: ExecutionContext = ExecutionContext.global) =
    new Http4sNetty {
      val route: Route = toMount
    }
}

abstract class Http4sNetty(implicit executor: ExecutionContext)
            extends ChannelInboundHandlerAdapter with Logging {

  def route: Route

  val serverSoftware = ServerSoftware("HTTP4S / Netty")

  private var enum: ChunkEnum = null

  // Just a front method to forward the request and finally release the buffer
  override def channelRead(ctx: ChannelHandlerContext, msg: Object) {
    try {
      doRead(ctx, msg)
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    logger.trace("Caught exception: %s", cause.getStackTraceString)
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

  private def doRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case req: http.HttpRequest =>
      assert(enum == null)
      startHttpRequest(ctx, req, ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)

    case c: http.LastHttpContent =>
      assert(enum != null)
      if (c.content().readableBytes() > 0)
        enum.push(buffToBodyChunk(c.content))

      if (!c.trailingHeaders().isEmpty)
        enum.push(TrailerChunk(toHeaders(c.trailingHeaders())))

      enum.close()
      enum = null

    case chunk: http.HttpContent =>
      assert(enum != null)
      enum.push(buffToBodyChunk(chunk.content))

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
    if (enum != null) stateError(req)
    else enum = new ChunkEnum
    val request = toRequest(ctx, req, rem)
    val parser = try { route.lift(request).getOrElse(Done(NotFound(request))) }
    catch { case t: Throwable => Done[Chunk, Responder](InternalServerError(t)) }

    val handler = parser.flatMap(renderResponse(ctx, req, _))
    enum.run[Unit](handler)
  }

  protected def chunkedIteratee(ctx: ChannelHandlerContext, f: ChannelFuture, isHttp10: Boolean, closeOnFinish: Boolean) = {
    // deal with the trailer or end of file after a chunked result
    def finisher(trailerChunk: Option[TrailerChunk], ctx: ChannelHandlerContext, lastOp: ChannelFuture): Iteratee[Chunk, Unit] = {
      if(!isHttp10) {
        val respTrailer = new http.DefaultLastHttpContent()
        for { c <- trailerChunk
              h <- c.headers    } respTrailer.trailingHeaders().set(h.name, h.value)

        val f = ctx.channel.writeAndFlush(respTrailer)
        if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
        Iteratee.flatten(f.map(ch => Done(())))

      } else {
        if (closeOnFinish) lastOp.addListener(ChannelFutureListener.CLOSE)
        Iteratee.flatten(lastOp.map(_ => Done(())))
      }
    }

    def chunkedFolder(ctx: ChannelHandlerContext, lastOp: ChannelFuture)(i: Input[Chunk]): Iteratee[Chunk, Unit] = i match {
      case Input.El(c: BodyChunk) =>
        val buff = Unpooled.wrappedBuffer(c.toArray)
        val f = ctx.channel().writeAndFlush(new DefaultHttpContent(buff))
        Cont(chunkedFolder(ctx, f))

      case Input.Empty => Cont(chunkedFolder(ctx, lastOp))

      case Input.El(c: TrailerChunk) if isHttp10 =>
        logger.warn("Received a trailer on a Http1.0 response. Trailer ignored.")
        Cont(chunkedFolder(ctx, lastOp))

      case Input.El(c: TrailerChunk)=> finisher(Some(c), ctx, lastOp)

      case Input.EOF => finisher(None, ctx, lastOp)
    }

    Cont(chunkedFolder(ctx, f))
  }

  protected def collectedIteratee(ctx: ChannelHandlerContext,
                                  protocolVersion: http.HttpVersion,
                                  stat: http.HttpResponseStatus,
                                  headers: List[(String, String)],
                                  length: Int,
                                  closeOnFinish: Boolean) = {
    val buff = ctx.alloc().buffer(length)

    // Folder method that just piles bytes into the buffer.
    def folder(in: Input[Chunk]): Iteratee[Chunk, Unit] = {
      if (ctx.channel().isOpen()) in match {
        case Input.El(c: BodyChunk) => buff.writeBytes(c.toArray); Cont(folder)
        case Input.El(e: TrailerChunk)  => sys.error("Chunk detected in nonchunked response: " + e)
        case Input.Empty => Cont(folder)
        case Input.EOF =>
          val resp = new DefaultFullHttpResponse(protocolVersion, stat, buff)
          headers.foreach { case (k,v) => resp.headers.set(k,v)}
          resp.headers().set(Names.CONTENT_LENGTH, buff.readableBytes())
          val f = ctx.channel.writeAndFlush(resp)
          if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
          Iteratee.flatten(f.map(_ => Done(())))

      } else Done(())
    }
    Cont(folder)
  }

  protected def renderResponse(ctx: ChannelHandlerContext, req: http.HttpRequest, responder: Responder): Iteratee[Chunk, Unit] = {

    val stat = new http.HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)

    val length = responder.prelude.headers.get(Headers.ContentLength).map(_.length)
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


    length.fold { // Start a chunked response, or just stream if its http1.0
      if(!isHttp10)    // Sets the chunked header
        headers += ((http.HttpHeaders.Names.TRANSFER_ENCODING, http.HttpHeaders.Values.CHUNKED))

      val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
      headers.foreach { case (k, v) => resp.headers.set(k,v) }
      val f = ctx.channel.writeAndFlush(resp)
      ctx.flush()
      responder.body &>> chunkedIteratee(ctx, f, isHttp10, closeOnFinish)
    } { length => // Known length, buffer the results and then send it out.
      responder.body &>> collectedIteratee(ctx, req.getProtocolVersion, stat, headers.result, length, closeOnFinish)
    }
  }

  private def toRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, remote: InetAddress)  = {
    val scheme = if (ctx.pipeline.get(classOf[SslHandler]) != null) "http" else "https"
    val uri = new URI(req.getUri)

    val servAddr = ctx.channel.remoteAddress.asInstanceOf[InetSocketAddress]
    RequestPrelude(
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
  }

  private def toHeaders(headers: http.HttpHeaders) = {
    import collection.JavaConversions._
    HeaderCollection( headers.entries.map(entry => RawHeader(entry.getKey, entry.getValue)):_*)
  }
}
