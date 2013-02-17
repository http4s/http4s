package org.http4s
package netty

import concurrent.{Future, ExecutionContext}
import handlers.ScalaUpstreamHandler
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
import scala.language.{ implicitConversions, reflectiveCalls }
import java.util.concurrent.LinkedBlockingDeque
import org.http4s.Responder
import scala.util.{ Failure, Success }
import org.http4s.RequestPrelude
import org.http4s.Status.NotFound


object Routes {
   def apply(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) = new Routes(route)
}
class Routes(val route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) extends Http4sNetty

class RequestChunksEnumerator(req: HttpRequest, chunks: LinkedBlockingDeque[HttpChunk] = new LinkedBlockingDeque[HttpChunk]()) extends Enumerator[HttpChunk]{

  def push(chunk: HttpChunk) = chunks.offer(chunk)

  def apply[A](i: Iteratee[HttpChunk, A]): Future[Iteratee[HttpChunk, A]] = {
    def step(i: Iteratee[HttpChunk, A]): Future[Iteratee[HttpChunk, A]] = i.fold({
      case Step.Cont(k) =>
        chunks.poll() match {
          case null if req.isChunked => Future.successful(k(Input.Empty))
          case null =>
            Future.successful(k(Input.El(new http.DefaultHttpChunk(req.getContent))).flatMap(r => Done(r, Input.EOF)))
          case trailer: HttpChunkTrailer => Future.successful(k(Input.EOF))
          case chunk: HttpChunk =>
            if (!req.isChunked || chunk.isLast) {
              Future.successful(k(Input.El(chunk)).flatMap(r => Done(r, Input.EOF)))
            } else {
              val ii = k(Input.El(chunk))
              Future.successful(ii)
            }
        }
      case Step.Done(r, re) =>
        Future.successful(Done(r, re))
      case Step.Error(msg, re) =>
        Future.failed(sys.error(msg))
    })
    step(i)
  }
}

abstract class Http4sNetty(implicit executor: ExecutionContext = ExecutionContext.global) extends ScalaUpstreamHandler {

  def route: Route

  import ScalaUpstreamHandler._

//  @volatile var enumerator: RequestChunksEnumerator = _

  val handle: UpstreamHandler = {
    case MessageReceived(ctx, req: HttpRequest, rem: InetSocketAddress) =>

//      enumerator = new RequestChunksEnumerator(req)

      val request = toRequest(ctx, req, rem.getAddress)
      if (route.isDefinedAt(request)) {
        val parser = route.lift(request).getOrElse(Done(NotFound(request)))
        val handler = parser flatMap (renderResponse(ctx, req, _))
        Enumerator(req.getContent.array()).map[org.http4s.HttpChunk](org.http4s.HttpEntity(_)).run[Unit](handler)
//        handler.feed(Input.El(HttpEntity(req.getContent.array()))) flatMap { i => i.feed(Input.EOF)}
//        responder onSuccess {
//          case r =>
//            renderResponse(ctx, req, r)
//        }
//        responder onFailure {
//          case t => t.printStackTrace()
//        }
      } else {
        import org.http4s.HttpVersion
        val res = new http.DefaultHttpResponse(HttpVersion.`Http/1.1`, Status.NotFound)
        res.setContent(ChannelBuffers.copiedBuffer("Not Found", Codec.UTF8.charSet))
        ctx.getChannel.write(res).addListener(ChannelFutureListener.CLOSE)
      }

    case MessageReceived(ctx, chunk: HttpChunk, _) =>
//      enumerator.push(chunk)
      sys.error("Not supported")

    case ExceptionCaught(ctx, e) =>
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

  protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, responder: Responder) = {
    def closeChannel(channel: Channel) = {
      if (!http.HttpHeaders.isKeepAlive(req) && channel.isConnected) channel.close()
    }


    val stat = new HttpResponseStatus(responder.prelude.status.code, responder.prelude.status.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
    for (header <- responder.prelude.headers) {
      resp.addHeader(header.name, header.value)
    }

    val channelBuffer = ChannelBuffers.dynamicBuffer(8912)
    val writer: (ChannelBuffer, org.http4s.HttpChunk) => Unit = (c, x) => c.writeBytes(x.bytes)
    val stringIteratee = Iteratee.fold(channelBuffer)((c, e: org.http4s.HttpChunk) => { writer(c, e); c })

    val p = (responder.body ><> Enumeratee.grouped(stringIteratee) &>> Cont {
      case Input.El(buffer) =>
        resp.setHeader(HttpHeaders.Names.CONTENT_LENGTH, channelBuffer.readableBytes)
        resp.setContent(buffer)
        val f = ctx.getChannel.write(resp)
        f onComplete {
          case _ => closeChannel(ctx.getChannel)
        }
        Iteratee.flatten(f.map(_ => Done((),Input.Empty:Input[org.jboss.netty.buffer.ChannelBuffer])))

      case other => Error("unexepected input",other)
    })
    p
//
//    val content = ChannelBuffers.buffer(512 * 1024)
//    resp.setContent(content)
//    val chf = Iteratee.foldM[Chunk, Option[HttpChunk]](None) {
//      case (None, chunk) if ((content.readableBytes() + chunk.length) <= content.writableBytes()) =>
//        content.writeBytes(chunk)
//        Future.successful(None)
//      case (None, chunk) =>
//        val msg = new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk))
//        if (!(responder.headers.exists(h => (h.name equalsIgnoreCase "TRANSFER-ENCODING") && h.value.equalsIgnoreCase("CHUNKED"))))
//          resp.addHeader(http.HttpHeaders.Names.TRANSFER_ENCODING, http.HttpHeaders.Values.CHUNKED)
//        ctx.getChannel.write(resp) map (_ => Some(msg))
//      case (Some(toWrite), chunk) =>
//        val msg = new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk))
//        ctx.getChannel.write(toWrite) map (_ => Some(msg))
//    }
//
//    responder.body.run(chf) flatMap {
//      case None =>
//        if (!responder.headers.exists(_.name equalsIgnoreCase "CONTENT-LENGTH"))
//          resp.addHeader("Content-Length", resp.getContent.readableBytes())
//        resp.setContent(content)
//        ctx.getChannel.write(resp)
//      case Some(chunk) =>
//        val trailer = new DefaultHttpChunkTrailer()
//        val ch = ctx.getChannel
//        ch.write(chunk) flatMap (_ write trailer)
//    } onComplete {
//      case Success(ch) =>
//        closeChannel(ch)
//      case Failure(t) =>
//        t.printStackTrace()
//        closeChannel(ctx.getChannel)
//    }

  }

  import HeaderNames._
  val serverSoftware = ServerSoftware("HTTP4S / Netty")

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

    RequestPrelude(
      requestMethod = Method(req.getMethod.getName),
      scriptName = "",
      pathInfo = uri.getRawPath,
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      urlScheme = UrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = serverSoftware,
      remote = remote // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(req: HttpRequest) = Headers(
    (for {
      name  <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield org.http4s.HttpHeaders.RawHeader(name, value)).toSeq:_*
  )
}