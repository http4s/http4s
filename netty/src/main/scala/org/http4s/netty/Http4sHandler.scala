package org.http4s
package netty

import concurrent.{Promise, Future, ExecutionContext}
import org.jboss.netty.channel._
import scala.util.control.Exception.allCatch
import org.jboss.netty.buffer.ChannelBuffers
import io.Codec
import play.api.libs.iteratee.{Iteratee, Concurrent, Enumerator}
import java.net.{InetSocketAddress, SocketAddress, URI, InetAddress}
import collection.JavaConverters._
import HeaderNames._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http
import http.DefaultHttpResponse
import http.HttpRequest
import http.HttpResponseStatus
import http.HttpVersion
import org.http4s.Responder
import org.http4s.Request
import org.http4s.Header
import util.{Failure, Success}
import scala.language.implicitConversions
import com.typesafe.scalalogging.slf4j.Logging

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

abstract class Http4sHandler(implicit executor: ExecutionContext = ExecutionContext.global) extends ScalaUpstreamHandler {

  def route: Route

  import ScalaUpstreamHandler._

  def handle: ScalaUpstreamHandler.UpstreamHandler = {
    case MessageReceived(ctx, req: HttpRequest, rem: InetSocketAddress) =>
      val request = toRequest(ctx, req, rem.getAddress)
      val handler = route(request)
      logger.info(s"Got request $request")
      val responder = request.body.run(handler)
      responder onSuccess {
        case responder =>
          logger.info(s"Response generated in the handler for request $request")
          renderResponse(ctx, req, responder)
      }
      responder onFailure {
        case t => logger.error(t.getMessage, t)
      }

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

  class Cancelled(val channel: Channel) extends Throwable
  class ChannelError(val channel: Channel, val reason: Throwable) extends Throwable
  implicit def channelFuture2Future(cf: ChannelFuture): Future[Channel] = {
    val prom = Promise[Channel]()
    cf.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          prom.complete(Success(future.getChannel))
        } else if (future.isCancelled) {
          prom.complete(Failure(new Cancelled(future.getChannel)))
        } else {
          prom.complete(Failure(new ChannelError(future.getChannel, future.getCause)))
        }
      }
    })
    prom.future
  }

  protected def renderResponse(ctx: ChannelHandlerContext, req: HttpRequest, responder: Responder) {
    val stat = new HttpResponseStatus(responder.statusLine.code, responder.statusLine.reason)
    val resp = new http.DefaultHttpResponse(req.getProtocolVersion, stat)
    for (header <- responder.headers) {
      resp.addHeader(header.name, header.value)
    }
    val it = Iteratee.foreach[Chunk] { chunk =>
      logger.info("Writing chunk")
      ctx.getChannel.write(new http.DefaultHttpChunk(ChannelBuffers.wrappedBuffer(chunk)))
    }
    def closeChannel(channel: Channel) = {
      logger.info("Closing the channel")
      if (!http.HttpHeaders.isKeepAlive(req)) channel.close()
    }

    ctx.getChannel.write(resp) onComplete {
      case Success(channel: Channel) =>
        responder.body.run(it) onComplete {
          case _ => closeChannel(channel)
        }

      case Failure(c: Cancelled) =>
        closeChannel(c.channel)

      case Failure(t: ChannelError) =>
        t.reason.printStackTrace()
        closeChannel(t.channel)

      case Failure(t) =>
        t.printStackTrace()
        closeChannel(ctx.getChannel)
    }



  }

  protected def toRequest(ctx: ChannelHandlerContext, req: HttpRequest, remote: InetAddress)  = {
    println("")
    val uri = URI.create(req.getUri)
    val hdrs = toHeaders(req)
    val scheme = {
      hdrs.get(XForwardedProto).flatMap(_.blankOption) orElse {
        val fhs = hdrs.get(FrontEndHttps).flatMap(_.blankOption)
        fhs.filter(_ equalsIgnoreCase "ON").map(_ => "https")
      } getOrElse {
        if (ctx.getPipeline.get(classOf[SslHandler]) != null) "http" else "https"
      }
    }
    val servAddr = ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress]
    Request(
      requestMethod = Method(req.getMethod.getName),
      scriptName = uri.getRawPath,
      pathInfo = uri.getRawPath,
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      body = Enumerator(req.getContent.array()),
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
    } yield Header(name, value)).toSeq:_*
  )
}