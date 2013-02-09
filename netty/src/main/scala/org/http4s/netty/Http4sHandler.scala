package org.http4s
package netty

import concurrent.ExecutionContext
import org.jboss.netty.channel._
import scala.util.control.Exception.allCatch
import org.jboss.netty.handler.codec.http.{HttpRequest, DefaultHttpResponse, HttpVersion, HttpResponseStatus}
import org.jboss.netty.buffer.ChannelBuffers
import io.Codec
import play.api.libs.iteratee.Enumerator
import java.net.{InetSocketAddress, SocketAddress, URI, InetAddress}
import collection.JavaConverters._
import HeaderNames._
import org.jboss.netty.handler.ssl.SslHandler

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
trait ScalaUpstreamHandler extends SimpleChannelUpstreamHandler {
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

class Http4sHandler(implicit executor: ExecutionContext = ExecutionContext.global) extends ScalaUpstreamHandler {

  import ScalaUpstreamHandler._

  def handle: ScalaUpstreamHandler.UpstreamHandler = {
    case MessageReceived(ctx, req: HttpRequest, rem: InetSocketAddress) =>
      toRequest(ctx, req, rem.getAddress)

    case ExceptionCaught(ctx, e) =>
      try {
        Option(e.getCause) foreach (_.printStackTrace())
        val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        resp.setContent(ChannelBuffers.copiedBuffer((e.getCause.getMessage + "\n" + e.getCause.getStackTraceString).getBytes(Codec.UTF8.charSet)))
        ctx.getChannel.write(resp).addListener(ChannelFutureListener.CLOSE)
      } catch {
        case t: Throwable =>
          t.printStackTrace()
          allCatch(ctx.getChannel.close().await())
      }
  }

  protected def toRequest(ctx: ChannelHandlerContext, req: HttpRequest, remote: InetAddress)  = {
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
      pathInfo = "",
      queryString = uri.getRawQuery,
      protocol = ServerProtocol(req.getProtocolVersion.getProtocolName),
      headers = hdrs,
      body = Enumerator.enumerate(Seq(req.getContent.array())),
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