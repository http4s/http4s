package org.http4s
package netty4

import scala.language.implicitConversions

import scala.concurrent.ExecutionContext
import scala.util.control.Exception.allCatch

import play.api.libs.iteratee._
import java.net.{InetSocketAddress, URI, InetAddress}


import io.netty.handler.ssl.SslHandler

import io.netty.handler.codec.http
import http.HttpHeaders.{Names, Values, isKeepAlive}

import org.http4s.{HttpHeaders, HttpChunk, Responder, RequestPrelude}
import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.TrailerChunk

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelInboundHandlerAdapter, ChannelHandlerContext}
import io.netty.util.{ReferenceCountUtil, CharsetUtil}
import io.netty.buffer.ByteBuf
import org.http4s.HttpHeaders.RawHeader
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.http.{DefaultHttpContent, DefaultFullHttpResponse}
import scala.collection.mutable.ListBuffer


object Http4sNetty {
  def apply(toMount: Route, contextPath: String= "/")(implicit executor: ExecutionContext = ExecutionContext.global) =
    new Http4sNetty(contextPath) {
      val route: Route = toMount
    }
}


abstract class Http4sNetty(val contextPath: String)(implicit executor: ExecutionContext)
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

  private def doRead(ctx: ChannelHandlerContext, msg: Object): Unit = msg match {
    case req: http.HttpRequest =>
      val uri = URI.create(req.getUri)
      if (uri.getRawPath.startsWith(contextPath + "/") || uri.getRawPath == contextPath) {
        startHttpRequest(ctx, req, ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress, uri)
      }
      else ctx.fireChannelRead(msg)   // Pass it upstream

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
  }

  private def stateError(o: AnyRef): Nothing = sys.error("Received invalid combination of chunks and stand parts. " + o.getClass)

  private def buffToBodyChunk(buff: ByteBuf) = {
    val arr = new Array[Byte](buff.readableBytes())
    buff.getBytes(0, arr)
    BodyChunk(arr)
  }

  private def startHttpRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, rem: InetAddress, uri: URI) {
    if (enum != null) stateError(req)
    else enum = new ChunkEnum
    val request = toRequest(ctx, req, rem, uri)
    val parser = try { route.lift(request).getOrElse(Done(NotFound(request))) }
    catch { case t: Throwable => Done[HttpChunk, Responder](InternalServerError(t)) }

    val handler = parser.flatMap(renderResponse(ctx, req, _))

    val _ = enum.run[Unit](handler)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
//    println("Caught exception!!! ----------------------------------------------------------\n" +  // DEBUG
//      (if(cause != null) cause.getStackTraceString else ""))
    try {
      if (ctx.channel.isOpen) {
        val msg = (cause.getMessage + "\n" + cause.getStackTraceString).getBytes(CharsetUtil.UTF_8)
        val buff = makeBuffer(ctx, msg)
        val resp = new http.DefaultFullHttpResponse(http.HttpVersion.HTTP_1_0, http.HttpResponseStatus.INTERNAL_SERVER_ERROR, buff)

        ctx.channel.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
      }
    } catch {
      case t: Throwable =>
        logger.error(t.getMessage, t)
        allCatch(ctx.close())
    }
  }

  private def makeBuffer(ctx: ChannelHandlerContext, array: Array[Byte]) = {
    val buf = ctx.alloc().buffer(array.length)
    buf.writeBytes(array)
    buf
  }

  protected def chunkedIteratee(ctx: ChannelHandlerContext, f: ChannelFuture, isHttp10: Boolean, closeOnFinish: Boolean) = {
    // deal with the trailer or end of file after a chunked result
    def finisher(trailerChunk: Option[TrailerChunk], ctx: ChannelHandlerContext, lastOp: ChannelFuture): Iteratee[HttpChunk, Unit] = {
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

    def chunkedFolder(ctx: ChannelHandlerContext, lastOp: ChannelFuture)(i: Input[HttpChunk]): Iteratee[HttpChunk, Unit] = i match {
      case Input.El(c: BodyChunk) =>
        val buff = makeBuffer(ctx, c.toArray)
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
    def folder(in: Input[HttpChunk]): Iteratee[HttpChunk, Unit] = {
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

  protected def renderResponse(ctx: ChannelHandlerContext, req: http.HttpRequest, responder: Responder): Iteratee[HttpChunk, Unit] = {

    val stat = new http.HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)

    val length = responder.prelude.headers.get(HttpHeaders.ContentLength).map(_.length)
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

  protected def toRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, remote: InetAddress, uri: URI)  = {
    val hdrs = toHeaders(req.headers)
    val scheme = if (ctx.pipeline.get(classOf[SslHandler]) != null) "http" else "https"

    val servAddr = ctx.channel.remoteAddress.asInstanceOf[InetSocketAddress]
    //println("pth info: " + uri.getPath.substring(contextPath.length))
    RequestPrelude(
      requestMethod = Method(req.getMethod.name),
      scriptName = contextPath,
      pathInfo = uri.getRawPath.substring(contextPath.length),
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.protocolName),
      headers = hdrs,
      urlScheme = HttpUrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = serverSoftware,
      remote = remote // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(headers: http.HttpHeaders) = {
    import collection.JavaConversions._
    HttpHeaders( headers.entries.map(entry => RawHeader(entry.getKey, entry.getValue)):_*)
  }
}
