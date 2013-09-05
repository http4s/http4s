package org.http4s
package netty

import scala.language.implicitConversions

import scala.concurrent.ExecutionContext
import org.jboss.netty.channel._
import scala.util.control.Exception.allCatch
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}

import io.Codec

import play.api.libs.iteratee._
import java.net.{InetSocketAddress, URI, InetAddress}
import collection.JavaConverters._

import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http

import org.jboss.netty.handler.codec.{ http => n}

import org.http4s.{HttpHeaders, HttpChunk, Responder, RequestPrelude}
import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.TrailerChunk
import org.jboss.netty.handler.codec.http.HttpHeaders.{Names, Values, isKeepAlive}
import com.typesafe.scalalogging.slf4j.Logging


object Http4sNetty {
  def apply(toMount: Route, contextPath: String= "/")(implicit executor: ExecutionContext = ExecutionContext.global) =
    new Http4sNetty(contextPath) {
      val route: Route = toMount
    }
}


abstract class Http4sNetty(val contextPath: String)(implicit executor: ExecutionContext)
            extends ChannelUpstreamHandler with Logging {

  def route: Route

  val serverSoftware = ServerSoftware("HTTP4S / Netty")

  private var readingChunks = false

  private var enum: ChunkEnum = null

  override def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent): Unit = e match {
    case me: MessageEvent =>
      me.getMessage() match {
        case req: n.HttpRequest =>
          val uri = URI.create(req.getUri)
          // Make this this request is for this handler
          if (uri.getRawPath.startsWith(contextPath + "/") || uri.getRawPath == contextPath) {
            if (readingChunks) stateError(req)
            startHttpRequest(ctx, req, me.getRemoteAddress.asInstanceOf[InetSocketAddress].getAddress, uri)
          } else ctx.sendUpstream(e)

        case chunk: n.HttpChunk =>
          if(!readingChunks) stateError(chunk)
          chunkReceived(ctx, chunk)

        case _ => ctx.sendUpstream(e)
      }

    case ex: ExceptionEvent => exceptionCaught(ctx, ex)

    case e => ctx.sendUpstream(e)
  }

  def chunkReceived(ctx: ChannelHandlerContext, chunk: n.HttpChunk) {
    if (chunk.isInstanceOf[n.HttpChunkTrailer]) sys.error("Trailer chunks not implemented.")  // TODO: need to implement trailers

    assert(enum != null)
    if(chunk.isLast) {
      enum.close()
      enum = null
      readingChunks = false
    } else enum.push(buffToBodyChunk(chunk.getContent))
  }

  private def stateError(o: AnyRef): Nothing = sys.error("Received invalid combination of chunks and stand parts. " + o.getClass)

  private def buffToBodyChunk(buff: ChannelBuffer) = {
    val arr = new Array[Byte](buff.readableBytes())
    buff.getBytes(0, arr)
    BodyChunk(arr)
  }

  private def getRequestBody(req: n.HttpRequest): Enumerator[HttpChunk] = {
    if (req.isChunked) {
      readingChunks = true
      enum = new ChunkEnum
      enum
    } else Enumerator.enumInput(Input.El(buffToBodyChunk(req.getContent)))
  }

  private def startHttpRequest(ctx: ChannelHandlerContext, req: n.HttpRequest, rem: InetAddress, uri: URI) {
    val request = toRequest(ctx, req, rem, uri)
    val parser = try { route.lift(request).getOrElse(Done(NotFound(request))) }
    catch { case t: Throwable => Done[HttpChunk, Responder](InternalServerError(t)) }

    val handler = parser.flatMap(renderResponse(ctx.getChannel, req, _))

    executor.execute(new Runnable {
      def run() {
        val _ = getRequestBody(req).run[Unit](handler)
      }
    })
  }

  def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
//    println("Caught exception!!! ----------------------------------------------------------\n" +  // DEBUG
//      (if(e.getCause != null) e.getCause.getStackTraceString else ""))
    try {
      if (ctx.getChannel.isOpen) {
        val resp = new n.DefaultHttpResponse(n.HttpVersion.HTTP_1_0, n.HttpResponseStatus.INTERNAL_SERVER_ERROR)
        resp.setContent(ChannelBuffers.copiedBuffer((e.getCause.getMessage + "\n" + e.getCause.getStackTraceString).getBytes(Codec.UTF8.charSet)))
        ctx.getChannel.write(resp).addListener(ChannelFutureListener.CLOSE)
      }
    } catch {
      case t: Throwable =>
        logger.error(t.getMessage, t)
        allCatch(ctx.getChannel.close())
    }
  }

  protected def chunkedIteratee(ch: Channel, f: ChannelFuture, isHttp10: Boolean, closeOnFinish: Boolean) = {
    // deal with the trailer or end of file after a chunked result
    def finisher(trailer: Option[TrailerChunk], ch: Channel, lastOp: ChannelFuture): Iteratee[HttpChunk, Unit] = {
      if(!isHttp10) {
        val chunk = new n.DefaultHttpChunkTrailer()
        for { c <- trailer
              h <- c.headers } chunk.addHeader(h.name, h.value)

        val f = ch.write(chunk)
        if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
        Iteratee.flatten(f.map(ch => Done(())))

      } else {
        if (closeOnFinish) lastOp.addListener(ChannelFutureListener.CLOSE)
        Iteratee.flatten(lastOp.map(_ => Done(())))
      }
    }

    def chunkedFolder(ch: Channel, lastOp: ChannelFuture)(i: Input[HttpChunk]): Iteratee[HttpChunk, Unit] = i match {
      case Input.El(c: BodyChunk) =>
        val buff = ChannelBuffers.wrappedBuffer(c.toArray)
        val f = ch.write(if(isHttp10) buff else new http.DefaultHttpChunk(buff))
        Cont(chunkedFolder(ch, f))

      case Input.Empty => Cont(chunkedFolder(ch, lastOp))

      case Input.El(c: TrailerChunk) if isHttp10 =>
        logger.warn("Received a trailer on a Http1.0 response. Trailer ignored.")
        Cont(chunkedFolder(ch, lastOp))

      case Input.El(c: TrailerChunk)=> finisher(Some(c), ch, lastOp)

      case Input.EOF =>finisher(None, ch, lastOp)
    }

    Cont(chunkedFolder(ch, f))
  }

  protected def collectedIteratee(ch: Channel, resp: n.HttpResponse, length: Int, closeOnFinish: Boolean) = {
    val buff = ChannelBuffers.dynamicBuffer(length)

    // Folder method that just piles bytes into the buffer.
    def folder(in: Input[HttpChunk]): Iteratee[HttpChunk, Unit] = {
      if (ch.isOpen()) in match {
        case Input.El(c: BodyChunk) => buff.writeBytes(c.toArray); Cont(folder)
        case Input.El(e: TrailerChunk)  => sys.error("Chunk detected in nonchunked response: " + e)
        case Input.Empty => Cont(folder)
        case Input.EOF =>
          resp.setHeader(Names.CONTENT_LENGTH, buff.readableBytes())
          resp.setContent(buff)
          val f = ch.write(resp)
          if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
          Iteratee.flatten(f.map(_ => Done(())))

      } else Done(())
    }
    Cont(folder)
  }

  protected def renderResponse(ch: Channel, req: n.HttpRequest, responder: Responder): Iteratee[HttpChunk, Unit] = {

    val stat = new n.HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)

    for (header <- responder.prelude.headers)
      resp.addHeader(header.name, header.value)

    val length = responder.prelude.headers.get(HttpHeaders.ContentLength).map(_.length)
    val isHttp10 = req.getProtocolVersion == n.HttpVersion.HTTP_1_0

    val closeOnFinish = if (isHttp10) {
      if (length.isEmpty && isKeepAlive(req)) {
        resp.setHeader(Names.CONNECTION, Values.CLOSE)
        true
      } else if(isKeepAlive(req)) {
        resp.setHeader(Names.CONNECTION, Values.KEEP_ALIVE)
        false
      } else true
    } else if (Values.CLOSE.equalsIgnoreCase(req.getHeader(Names.CONNECTION))){  // Http 1.1+
      resp.setHeader(Names.CONNECTION, Values.CLOSE)
      true
    } else false


    length.fold { // Start a chunked response, or just stream if its http1.0
      resp.setChunked(!isHttp10)    // Sets the chunked header
      val f = ch.write(resp)
      responder.body &>> chunkedIteratee(ch, f, isHttp10, closeOnFinish)
    } { length => // Known length, buffer the results and then send it out.
      responder.body &>> collectedIteratee(ch, resp, length, closeOnFinish)
    }
  }

  protected def toRequest(ctx: ChannelHandlerContext, req: n.HttpRequest, remote: InetAddress, uri: URI)  = {
    val hdrs = toHeaders(req)
    val scheme = if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https"

    val servAddr = ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress]
    //println("pth info: " + uri.getPath.substring(contextPath.length))
    RequestPrelude(
      requestMethod = Method(req.getMethod.getName),
      scriptName = contextPath,
      pathInfo = uri.getRawPath.substring(contextPath.length),
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      urlScheme = HttpUrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = serverSoftware,
      remote = remote // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(req: n.HttpRequest) = org.http4s.HttpHeaders(
    (for {
      name  <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield org.http4s.HttpHeaders.RawHeader(name, value)).toSeq:_*
  )
}
