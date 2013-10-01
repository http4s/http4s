
package org.http4s
package netty

import scala.concurrent.ExecutionContext
import scala.util.control.Exception.allCatch

import java.net.{InetSocketAddress, URI, InetAddress}


import io.netty.handler.ssl.SslHandler

import io.netty.handler.codec.http
import http.HttpHeaders.{Names, Values, isKeepAlive}

import org.http4s.{HttpHeaders, HttpChunk, Response, RequestPrelude}
import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.TrailerChunk

import com.typesafe.scalalogging.slf4j.Logging

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelInboundHandlerAdapter, ChannelHandlerContext}
import io.netty.util.{ReferenceCountUtil, CharsetUtil}
import io.netty.buffer.{Unpooled, ByteBuf}
import org.http4s.HttpHeaders.RawHeader
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.http.{HttpServerCodec, DefaultHttpContent, DefaultFullHttpResponse}
import scala.collection.mutable.ListBuffer
import scalaz.concurrent.Task
import scalaz.{-\/, \/-, \/}
import scalaz.stream.Process._
import scala.util.control.NonFatal
import scalaz.stream.Process


object Http4sNetty {
  def apply(toMount: HttpService[Task]) =
    new Http4sNetty {
      val service = toMount
    }
}


abstract class Http4sNetty
            extends ChannelInboundHandlerAdapter with Logging {

  def service: HttpService[Task]

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

      if (!c.trailingHeaders().isEmpty)
        cb(\/-(TrailerChunk(toHeaders(c.trailingHeaders()))))

      cb(-\/(End))
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
    val parser = service(request)
      .flatMap( resp => renderResponse(ctx, req, resp.prelude))
      .handle { case NonFatal(e) =>
      logger.error("Error handling request", e)
      val msg = ("500 Error\nTrace:\n" + e.getMessage + "\n" + e.getStackTraceString).getBytes(CharsetUtil.UTF_8)
      val buff = Unpooled.wrappedBuffer(msg)
      val resp = new http.DefaultFullHttpResponse(http.HttpVersion.HTTP_1_0, http.HttpResponseStatus.INTERNAL_SERVER_ERROR, buff)

      ctx.channel.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
      Process.Halt(e)
    }
    println("Request Running.")
    parser.run.runAsync( _ => ())
  }

  /*
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
  }    */

  protected def renderResponse(ctx: ChannelHandlerContext, req: http.HttpRequest, respPrelude: ResponsePrelude): Sink[Task, HttpChunk] = {

    val stat = new http.HttpResponseStatus(respPrelude.status.code, respPrelude.status.reason)

    val length = respPrelude.headers.get(HttpHeaders.ContentLength).map(_.length)
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



  private def toRequest(ctx: ChannelHandlerContext, req: http.HttpRequest, remote: InetAddress): Request[Task] = {
    val scheme = if (ctx.pipeline.get(classOf[SslHandler]) != null) "http" else "https"
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

    def go(step: Task[HttpChunk]): Process[Task, HttpChunk] = {
      await[Task, HttpChunk, HttpChunk](step)( _ match {
        case chunk: TrailerChunk =>  emit(chunk) ++ halt
        case chunk: HttpChunk => emit(chunk) ++ go(step)
      })
    }
    val t = Task.async[HttpChunk]{ register =>
      println("Registering callback.")
      cb = register
    }

    val body = go(t)
    Request(prelude, body)
  }

  private def toHeaders(headers: http.HttpHeaders) = {
    import collection.JavaConversions._
    HttpHeaders( headers.entries.map(entry => RawHeader(entry.getKey, entry.getValue)):_*)
  }
}

