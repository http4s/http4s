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
import http._
import http.{HttpChunk => NettyChunk}

import org.http4s.{HttpHeaders, HttpChunk, Responder, RequestPrelude}
import org.http4s.Status.{InternalServerError, NotFound}
import org.http4s.HttpEncodings._
import org.http4s.TrailerChunk
import org.jboss.netty.handler.codec.http.HttpHeaders.{Names, Values, isKeepAlive}
import com.typesafe.scalalogging.slf4j.Logging
import scala.util.Success


object Http4sNetty {
  def apply(toMount: Route, contextPath: String= "/")(implicit executor: ExecutionContext = ExecutionContext.global) =
    new Http4sNetty(contextPath) {
      val route: Route = toMount
    }
}


abstract class Http4sNetty(val contextPath: String)(implicit executor: ExecutionContext)
            extends SimpleChannelUpstreamHandler with Logging {

  def route: Route

  val serverSoftware = ServerSoftware("HTTP4S / Netty")

  private var readingChunks = false

  private var enum: ChunkEnum = null

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage() match {
      case req :HttpRequest if !readingChunks =>

        val rem = e.getRemoteAddress.asInstanceOf[InetSocketAddress]
        startHttpRequest(ctx, req, rem.getAddress)

      case ch: NettyChunk if readingChunks =>
        assert(enum != null)
        if(ch.isLast) {
          enum.close()
          enum = null
          readingChunks = false
        } else enum.push(buffToBodyChunk(ch.getContent))

      case tr: HttpChunkTrailer =>
        sys.error("Trailer chunks not implemented.")  // TODO: need to implement trailers

      case e => sys.error("Received invalid combination of chunks and stand parts. " + e.getClass)
    }
  }

  private def buffToBodyChunk(buff: ChannelBuffer) = {
    val arr = new Array[Byte](buff.readableBytes())
    buff.getBytes(0, arr)
    BodyChunk(arr)
  }

  private def getEnumerator(req: HttpRequest): Enumerator[HttpChunk] = {
    if (req.isChunked) {
      readingChunks = true
      enum = new ChunkEnum
      enum
    } else Enumerator.enumInput(Input.El(buffToBodyChunk(req.getContent)))
  }

  private def startHttpRequest(ctx: ChannelHandlerContext, req: HttpRequest, rem: InetAddress) {

    val request = toRequest(ctx, req, rem)
    val parser = try { route.lift(request).getOrElse(Done(NotFound(request))) }
    catch { case t: Throwable => Done[HttpChunk, Responder](InternalServerError(t)) }

    val handler = parser.flatMap(renderResponse(ctx.getChannel, req, _))
//    executor.execute(new Runnable {
//      def run() {
        val _ = getEnumerator(req).run[Unit](handler)
//      }
//    })
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    println("Caught exception!!! ----------------------------------------------------------\n" + e.getCause.getStackTraceString)
    try {
      Option(e.getCause) foreach (t => logger.error(t.getMessage, t))
      val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      resp.setContent(ChannelBuffers.copiedBuffer((e.getCause.getMessage + "\n" + e.getCause.getStackTraceString).getBytes(Codec.UTF8.charSet)))
      ctx.getChannel.write(resp).addListener(ChannelFutureListener.CLOSE)
    } catch {
      case t: Throwable =>
        logger.error(t.getMessage, t)
        allCatch(ctx.getChannel.close().await())
    }
  }

  protected def renderResponse(ch: Channel, req: HttpRequest, responder: Responder): Iteratee[HttpChunk, Unit] = {

    val stat = new HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)

    for (header <- responder.prelude.headers) {
      resp.addHeader(header.name, header.value)
    }

    val length = responder.prelude.headers.get(HttpHeaders.ContentLength).map(_.length)
    val isHttp10 = req.getProtocolVersion == HttpVersion.HTTP_1_0

    // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
//    val closeOnFinish = if (isHttp10 && length.isEmpty && isKeepAlive(req)) {
//      resp.setHeader(Names.CONNECTION, Values.CLOSE)
//      true
//    } else if(isHttp10 && isKeepAlive(req)) {
//      resp.setHeader(Names.CONNECTION, Values.KEEP_ALIVE)
//      false
//    } else if(isHttp10)  true
//      // Http 1.1+
//    else if (Values.CLOSE.equalsIgnoreCase(req.getHeader(Names.CONNECTION))){
//      resp.setHeader(Names.CONNECTION, Values.CLOSE)
//      true
//    } else false
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


    if (length.isEmpty) { // Start a chunked response, or just stream if its http1.0
      def chunkedFolder(ch: Channel, lastOp: ChannelFuture)(i: Input[HttpChunk]): Iteratee[HttpChunk, Unit] = i match {
        case Input.El(c: BodyChunk) =>
          val buff = ChannelBuffers.wrappedBuffer(c.toArray)
          val f = ch.write(if(isHttp10) buff else new http.DefaultHttpChunk(buff))
          Cont(chunkedFolder(ch, f))

        case Input.El(c: TrailerChunk) =>
          logger.warn("Not a chunked response. Trailer ignored.")
          Cont(chunkedFolder(ch, lastOp))

        case Input.Empty => Cont(chunkedFolder(ch, lastOp))

        case Input.EOF =>
          if(!isHttp10) {
            val f = ch.write(new DefaultHttpChunkTrailer)
            if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
            Iteratee.flatten(f.map(ch => Done(())))
          } else {
            if (closeOnFinish) lastOp.addListener(ChannelFutureListener.CLOSE)
            Iteratee.flatten(lastOp.map(_ => Done(())))
          }
      }

      resp.setChunked(!isHttp10)    // Sets the chunked header
      val f = ch.write(resp)
      responder.body &>> Cont(chunkedFolder(ch, f))

    } else {
      val buff = ChannelBuffers.dynamicBuffer(length.getOrElse(8*1028))

      // Folder method that just piles bytes into the buffer.
      def folder(in: Input[HttpChunk]): Iteratee[HttpChunk, Unit] = {
        if (ch.isOpen()) in match {
          case Input.El(c: BodyChunk) => buff.writeBytes(c.toArray); Cont(folder)
          case Input.El(e)  => sys.error("Not supported chunk type")
          case Input.Empty => Cont(folder)
          case Input.EOF =>
            resp.setHeader(Names.CONTENT_LENGTH, buff.readableBytes())
            resp.setContent(buff)
            val f = ch.write(resp)
            if (closeOnFinish) f.addListener(ChannelFutureListener.CLOSE)
            Iteratee.flatten[HttpChunk, Unit](f.map(_ => Done(())))

        } else Done(())
      }

      responder.body &>> Cont(folder)
    }
  }

  protected def toRequest(ctx: ChannelHandlerContext, req: HttpRequest, remote: InetAddress)  = {
    val uri = URI.create(req.getUri)
    val hdrs = toHeaders(req)
    val scheme = if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https" 
//    val scheme = {
//      hdrs.get(XForwardedProto).flatMap(_.value.blankOption) orElse {
//        val fhs = hdrs.get(FrontEndHttps).flatMap(_.value.blankOption)
//        fhs.filter(_ equalsIgnoreCase "ON").map(_ => "https")
//      } getOrElse {
//        if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https"
//      }
//    }
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

  protected def toHeaders(req: HttpRequest) = org.http4s.HttpHeaders(
    (for {
      name  <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield org.http4s.HttpHeaders.RawHeader(name, value)).toSeq:_*
  )
}