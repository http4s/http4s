package org.http4s
package netty

import concurrent.{Future, ExecutionContext}
import org.jboss.netty.channel._
import scala.util.control.Exception.allCatch
import org.jboss.netty.buffer.ChannelBuffers
import io.Codec
import play.api.libs.iteratee._
import java.net.{InetSocketAddress, SocketAddress, URI, InetAddress}
import collection.JavaConverters._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http
import http._
import scala.language.{ implicitConversions, reflectiveCalls }
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.LinkedBlockingDeque
import org.http4s.Responder
import scala.util.{ Failure, Success }
import org.http4s.Request
import spray.http.HttpHeaders

object ScalaUpstreamHandler {
  sealed trait NettyHandlerMessage
  sealed trait NettyUpstreamHandlerEvent extends NettyHandlerMessage
  case class MessageReceived(ctx: ChannelHandlerContext, e: Any, remoteAddress: SocketAddress) extends NettyUpstreamHandlerEvent
  case class ExceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) extends NettyUpstreamHandlerEvent
  case class ChannelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelBound(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelUnbound(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelInterestChanged(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChannelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class WriteComplete(ctx: ChannelHandlerContext, e: WriteCompletionEvent) extends NettyUpstreamHandlerEvent
  case class ChildChannelOpen(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) extends NettyUpstreamHandlerEvent
  case class ChildChannelClosed(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) extends NettyUpstreamHandlerEvent

  type UpstreamHandler = PartialFunction[NettyUpstreamHandlerEvent, Unit]
}
trait ScalaUpstreamHandler extends SimpleChannelUpstreamHandler with Logging {
  import ScalaUpstreamHandler._
  def handle: UpstreamHandler

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val m = MessageReceived(ctx, e.getMessage, e.getRemoteAddress)
    if (handle.isDefinedAt(m)) handle(m) else super.messageReceived(ctx, e)
  }
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    val m = ExceptionCaught(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.exceptionCaught(ctx, e)
  }
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelOpen(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelOpen(ctx, e)
  }
  override def channelBound(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelBound(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelBound(ctx, e)
  }
  override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelConnected(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelConnected(ctx, e)
  }
  override def channelInterestChanged(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelInterestChanged(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelInterestChanged(ctx, e)
  }
  override def channelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelDisconnected(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelDisconnected(ctx, e)
  }
  override def channelUnbound(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelUnbound(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelUnbound(ctx, e)
  }
  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val m = ChannelClosed(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.channelClosed(ctx, e)
  }
  override def writeComplete(ctx: ChannelHandlerContext, e: WriteCompletionEvent) {
    val m = WriteComplete(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.writeComplete(ctx, e)
  }
  override def childChannelOpen(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) {
    val m = ChildChannelOpen(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.childChannelOpen(ctx, e)
  }
  override def childChannelClosed(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) {
    val m = ChildChannelClosed(ctx, e)
    if (handle.isDefinedAt(m)) handle(m) else super.childChannelClosed(ctx, e)
  }
}

object Routes {
  def apply(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) = new Routes(route)
}
class Routes(val route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) extends Http4sHandler

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

abstract class Http4sHandler(implicit executor: ExecutionContext = ExecutionContext.global) extends ScalaUpstreamHandler {

  def route: Route

  import ScalaUpstreamHandler._

  @volatile var enumerator: RequestChunksEnumerator = _

  val handle: UpstreamHandler = {
    case MessageReceived(ctx, req: HttpRequest, rem: InetSocketAddress) =>
      println("offering another request")
      enumerator = new RequestChunksEnumerator(req)
      val request = toRequest(ctx, req, rem.getAddress)
      val responder = route(request)
      logger.info(s"Got request $request")
      responder onSuccess {
        case r =>
          logger.info(s"Response generated in the handler for request $request")
          renderResponse(ctx, req, r)
      }
      responder onFailure {
        case t => t.printStackTrace()
      }

    case MessageReceived(ctx, chunk: HttpChunk, _) =>
      enumerator.push(chunk)

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

  protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, responder: Responder[Raw]) {
    def closeChannel(channel: Channel) = {
      logger.info("Closing the channel")
      if (!http.HttpHeaders.isKeepAlive(req)) channel.close()
    }


    val stat = new HttpResponseStatus(responder.statusLine.code, responder.statusLine.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
    for (header <- responder.headers) {
      resp.addHeader(header.name, header.value)
    }

    val content = ChannelBuffers.buffer(512 * 1024)
    resp.setContent(content)
    val chf = Iteratee.foldM[Chunk, Option[HttpChunk]](None) {
      case (None, chunk) if ((content.readableBytes() + chunk.length) <= content.writableBytes()) =>
        println("collecting response buffer chunks")
        content.writeBytes(chunk)
        Future.successful(None)
      case (None, chunk) =>
        val msg = new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk))
        if (!(responder.headers.exists(h => (h.name equalsIgnoreCase "TRANSFER-ENCODING") && h.value.equalsIgnoreCase("CHUNKED"))))
          resp.addHeader(http.HttpHeaders.Names.TRANSFER_ENCODING, http.HttpHeaders.Values.CHUNKED)
        ctx.getChannel.write(resp) map (_ => Some(msg))
      case (Some(toWrite), chunk) =>
        val msg = new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk))
        ctx.getChannel.write(toWrite) map (_ => Some(msg))
    }

    responder.body.run(chf) flatMap {
      case None =>
        if (!responder.headers.exists(_.name equalsIgnoreCase "CONTENT-LENGTH"))
          resp.addHeader("Content-Length", resp.getContent.readableBytes())
        resp.setContent(content)
        ctx.getChannel.write(resp)
      case Some(chunk) =>
        val trailer = new DefaultHttpChunkTrailer()
        val ch = ctx.getChannel
        ch.write(chunk) flatMap (_ write trailer)
    } onComplete {
      case Success(ch) =>
        closeChannel(ch)
      case Failure(t) =>
        t.printStackTrace()
        closeChannel(ctx.getChannel)
    }

  }

  import HeaderNames._

  protected def toRequest(ctx: ChannelHandlerContext, req: HttpRequest, remote: InetAddress)  = {
    val uri = URI.create(req.getUri)
    val hdrs = toHeaders(req)
    val scheme = {
      hdrs.get(XForwardedProto).flatMap(_.value.blankOption) orElse {
        val fhs = hdrs.get(FrontEndHttps).flatMap(_.value.blankOption)
        fhs.filter(_ equalsIgnoreCase "ON").map(_ => "https")
      } getOrElse {
        if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https"
      }
    }
    val servAddr = ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress]

    Request(
      requestMethod = Method(req.getMethod.getName),
      scriptName = "",
      pathInfo = uri.getRawPath,
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      body = enumerator &> Enumeratee.map[HttpChunk](_.getContent.array()),
      urlScheme = UrlScheme(scheme),
      serverName = servAddr.getHostName,
      serverPort = servAddr.getPort,
      serverSoftware = ServerSoftware("HTTP4S/Netty"),
      remote = remote // TODO using remoteName would trigger a lookup
    )
  }

  protected def toHeaders(req: HttpRequest) = Headers(
    (for {
      name  <- req.getHeaderNames.asScala
      value <- req.getHeaders(name).asScala
    } yield HttpHeaders.RawHeader(name, value)).toSeq:_*
  )
}