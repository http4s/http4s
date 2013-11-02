
package org.http4s
package netty

import scala.concurrent.ExecutionContext
import scala.util.control.Exception.allCatch

import java.net.{InetSocketAddress, URI, InetAddress}


import io.netty.handler.ssl.SslHandler

import io.netty.handler.codec.http
import http.HttpHeaders.{Names, Values, isKeepAlive}

import org.http4s.Headers
import org.http4s.TrailerChunk

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelInboundHandlerAdapter, ChannelHandlerContext}
import io.netty.util.{ReferenceCountUtil, CharsetUtil}
import io.netty.buffer.{Unpooled, ByteBuf}
import org.http4s.Headers.RawHeader
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.http.{HttpServerCodec, DefaultHttpContent, DefaultFullHttpResponse}
import scala.collection.mutable.ListBuffer
import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}
import scalaz.stream.Process._
import scalaz.stream.async


object Http4sNetty {
  def apply(toMount: HttpService) =
    new Http4sNetty {
      val service = toMount
    }
}

abstract class Http4sNetty
            extends ChannelInboundHandlerAdapter with Logging {

  def service: HttpService

  val serverSoftware = ServerSoftware("HTTP4S / Netty")

  //private var enum: ChunkEnum = null
  private var cb: Throwable \/ HttpChunk => Unit = null

  // Just a front method to forward the request and finally release the buffer
  override def channelRead(ctx: ChannelHandlerContext, msg: Object) {
    println("Channel reading.")
    try {
      doRead(ctx, msg)
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    logger.trace("Caught exception: %s", cause.getStackTraceString)
    println(s"Caught an exception: ${cause.getClass}\n" + cause.getStackTraceString)
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
      println("Netty request received")
      assert(cb == null)
      startHttpRequest(ctx, req, ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)

    case c: http.LastHttpContent =>
      println(s"Netty last Content received. $cb")
      assert(cb != null)
      if (c.content().readableBytes() > 0)
        cb(\/-(buffToBodyChunk(c.content)))
      cb(\/-(TrailerChunk(toHeaders(c.trailingHeaders()))))
      cb = null

    case chunk: http.HttpContent =>
      println("Netty content received.")
      assert(cb != null)
      cb(\/-(buffToBodyChunk(chunk.content)))

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
    println("Starting httprequest.")
    if (cb != null) stateError(req)

    val request = toRequest(ctx, req, rem)
    //val parser = try { route.lift(request).getOrElse(Done(NotFound(request))) }
    //catch { case t: Throwable => Done[HttpChunk, Response](InternalServerError(t)) }
    val task = service(request)/*.handle {
      case e =>
        e.printStackTrace()
        InternalServerError()
    }*/.map { resp =>
      renderResponse(ctx, req, resp.prelude)
    }
    println("Request Running.")
    task.run
  }


  protected def renderResponse(ctx: ChannelHandlerContext, req: http.HttpRequest, respPrelude: ResponsePrelude): Sink[Task, HttpChunk] = {

    val stat = new http.HttpResponseStatus(respPrelude.status.code, respPrelude.status.reason)

    val length = respPrelude.headers.get(Headers.ContentLength).map(_.length)
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


    //length.fold { // Start a chunked response, or just stream if its http1.0
      if(!isHttp10)    // Sets the chunked header
        headers += ((http.HttpHeaders.Names.TRANSFER_ENCODING, http.HttpHeaders.Values.CHUNKED))

      val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
      headers.foreach { case (k, v) => resp.headers.set(k,v) }
      if (length.isEmpty) ctx.channel.writeAndFlush(resp)
      else ctx.channel.write(resp)   // Future

      //type Sink[+F[_],-O] = Process[F, O => F[Unit]]
      val sink: Sink[Task, HttpChunk] = emit((chunk:HttpChunk) => chunk match {
          case c: BodyChunk =>
            if (length.isEmpty) ctx.channel().writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray)))
            else ctx.channel().write(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray)))
            Task.now(())
          case c: TrailerChunk =>

            val respTrailer = new http.DefaultLastHttpContent()
            for ( h <- c.headers ) respTrailer.trailingHeaders().set(h.name, h.value)

            val f = ctx.channel.writeAndFlush(respTrailer)
            if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
            Task.now(())
        })
      sink
  }



  private def toRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, remote: InetAddress): Request = {
    val scheme = if (ctx.pipeline.get(classOf[SslHandler]) != null) "http" else "https"
    println("Received request: " + req.getUri)
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

    val (queue, body) = async.localQueue[HttpChunk]
    cb = {
      case \/-(chunk: BodyChunk) =>
        logger.info("enqueued chunk")
        queue.enqueue(chunk)
      case \/-(chunk: TrailerChunk) =>
        logger.info("enqueued trailer")
        queue.enqueue(chunk)
        queue.close
      case -\/(err) =>
        logger.error("received error", err)
        queue.fail(err)
    }

    Request(prelude, body)
  }

  private def toHeaders(headers: http.HttpHeaders) = {
    import collection.JavaConversions._
    HeaderCollection( headers.entries.map(entry => RawHeader(entry.getKey, entry.getValue)):_*)
  }
}

