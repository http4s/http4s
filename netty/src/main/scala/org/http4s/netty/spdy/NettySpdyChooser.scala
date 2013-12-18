package org.http4s.netty.spdy

import org.http4s.netty.http.NettyHttpHandler
import org.http4s.HttpService

import java.util.List
import java.util.concurrent.ExecutorService

import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.spdy.SpdyFrameCodec
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.channel.ChannelHandlerContext
import io.netty.buffer.ByteBuf

import org.eclipse.jetty.npn.NextProtoNego
import org.http4s.netty.NPNProvider

/**
 * @author Bryce Anderson
 *         Created on 12/16/13
 */
class NettySpdyChooser(service: HttpService)(implicit es: ExecutorService) extends ByteToMessageDecoder {
  def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: List[AnyRef]): Unit = {

    getProtocol(ctx) match {
      case "spdy/3.1" => insertSpdy(ctx)
      case _          => insertHttp1(ctx)
    }

    // remove ourselves from the pipeline
    ctx.pipeline.remove(this)
  }

  private def insertSpdy(ctx: ChannelHandlerContext) {
    val p = ctx.pipeline()
    val ch = ctx.channel().asInstanceOf[SocketChannel]
    p.addLast("spdyframecodec", new SpdyFrameCodec(3))    // TODO: Don't hard code SPDY version
      .addLast("http4s", new NettySpdyServerHandler(
      service,
      ch.localAddress,
      ch.remoteAddress,
      3,
      es))
  }

  private def insertHttp1(ctx: ChannelHandlerContext) {
    val p = ctx.pipeline()
    val ch = ctx.channel().asInstanceOf[SocketChannel]
    p.addLast("httpcodec", new HttpServerCodec())
    p.addLast("http4s", new NettyHttpHandler(service, ch.localAddress, ch.remoteAddress, es))
  }

  private def getProtocol(ctx: ChannelHandlerContext): String = {
    val ssl = ctx.pipeline().get(classOf[SslHandler])
    if (ssl != null) {
      NextProtoNego.get(ssl.engine()).asInstanceOf[NPNProvider].get
    }
    else ""
  }

}
