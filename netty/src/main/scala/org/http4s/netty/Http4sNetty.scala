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
        val req = e.getMessage().asInstanceOf[HttpRequest]
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
    } else {  Enumerator.enumInput(Input.El(buffToBodyChunk(req.getContent))) }
  }

  private def startHttpRequest(ctx: ChannelHandlerContext, req: HttpRequest, rem: InetAddress) {

    val request = toRequest(ctx, req, rem)
    val parser = try { route.lift(request).getOrElse(Done(NotFound(request))) }
    catch { case t: Throwable => Done[HttpChunk, Responder](InternalServerError(t)) }

    val keepAlive = isKeepAlive(req)

    val handler = parser.flatMap(renderResponse(ctx.getChannel, keepAlive, req, _))

    val cf = getEnumerator(req).run[Channel](handler)
    if(!keepAlive) cf.onComplete ( _ => ctx.getChannel.close() )
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    println("Caught exception!!! ----------------------------------------------------------")
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

  protected def renderResponse(ch: Channel, keepAlive: Boolean, req: HttpRequest, responder: Responder): Iteratee[HttpChunk, Channel] = {

    val stat = new HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)

    for (header <- responder.prelude.headers) {
      resp.addHeader(header.name, header.value)
    }

    val isChunked = responder.prelude.headers.get(HttpHeaders.TransferEncoding)
                        .map(_.coding.matches(chunked)).getOrElse(false)

    // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
    if (keepAlive) resp.setHeader(Names.CONNECTION, Values.KEEP_ALIVE)

    if (isChunked && ch.isConnected) {
      resp.setChunked(true)
      ch.write(resp)
      responder.body &>> Cont(chunkedFolder(ch))
    } else if (ch.isConnected) {

      val buff = ChannelBuffers.dynamicBuffer(1028)

      // Folder method that just piles bytes into the buffer.
      def folder(in: Input[HttpChunk]): Iteratee[HttpChunk, Channel] = in match {
        case Input.El(c: BodyChunk) => buff.writeBytes(c.toArray); Cont(folder)
        case Input.El(e)  => sys.error("Not supported chunk type")
        case Input.Empty => Cont(folder)
        case Input.EOF =>
          resp.setHeader(Names.CONTENT_LENGTH, buff.readableBytes())
          resp.setContent(buff)
          if (ch.isConnected) Iteratee.flatten[HttpChunk, Channel](ch.write(resp).map(c => Done(c)))
          else {  Done(ch) }
      }

      responder.body &>> Cont(folder)
    } else Done(ch)
  }

  private def writeChunk(data: Array[Byte], c: Channel) = {
    val buff = new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(data))
    c.write(buff)
  }

  private def chunkedFolder(ch: Channel)(i: Input[HttpChunk]): Iteratee[HttpChunk, Channel] = i match {
    case Input.El(c: BodyChunk) =>
      //println("Folding.")
      if(ch.isConnected) {
        writeChunk(c.toArray, ch)
        Cont(chunkedFolder(ch))
      } else Done(ch)

    case Input.El(c: TrailerChunk) =>
      logger.warn("Not a chunked response. Trailer ignored.")
      Cont(chunkedFolder(ch))

    case Input.Empty => Cont(chunkedFolder(ch))

    case Input.EOF =>
      if(ch.isConnected) {
        writeChunk(new Array[Byte](0), ch)   // EOF for chunked content
      }
      Done(ch)
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