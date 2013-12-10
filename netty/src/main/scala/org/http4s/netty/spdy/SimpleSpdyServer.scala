package org.http4s.netty.spdy

import javax.net.ssl.SSLContext
import java.lang.Boolean

import scala.concurrent.ExecutionContext
import io.netty.channel.{ChannelOption, ChannelInitializer, Channel}

import java.util.List
import io.netty.channel.nio.NioEventLoopGroup
import org.eclipse.jetty.npn.NextProtoNego
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.spdy.SpdyFrameCodec
import java.net.InetSocketAddress
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s._

/**
* @author Bryce Anderson
*         Created on 11/28/13
*/

object SimpleSpdyServer {
  def apply(sslContext: SSLContext, port: Int = 443)
           (service: HttpService)(implicit executionContext: ExecutionContext = ExecutionContext.global) =
    new SimpleSpdyServer(sslContext, port, service)
}

class NPNProvider(channel: Channel) extends NextProtoNego.ServerProvider with Logging {
  private var selected = ""
  def protocolSelected(protocol: String) {
    logger.debug("DEBUG: Selected protocol: " + protocol)
    selected = protocol
  }

  def unsupported() {
    channel.close()
  }

  def protocols(): List[String] = {
    import collection.JavaConversions._
    "spdy/3.1"::Nil
  }
}
class SimpleSpdyServer(sslContext: SSLContext, port: Int, service: HttpService)
                      (implicit executionContext: ExecutionContext = ExecutionContext.global) extends Logging {

    private val bossThreadPool = new NioEventLoopGroup()
    private val workerThreadPool = new NioEventLoopGroup()

    private def newssl(channel: Channel) = {
      val eng = sslContext.createSSLEngine()
      eng.setUseClientMode(false)
      val p = new NPNProvider(channel)
      NextProtoNego.put(eng, p)
      eng
    }

    def run() {
      try {
        val bootstrap = new ServerBootstrap()
          .group(bossThreadPool, workerThreadPool)
          .channel(classOf[NioServerSocketChannel])
          .childHandler(new ChannelInitializer[SocketChannel] {
          def initChannel(ch: SocketChannel) {
            logger.trace(s"Creating new channel. Local: ${ch.localAddress()}, remote: ${ch.remoteAddress()}")
            val ec = ExecutionContext.fromExecutor(ch.eventLoop())
            ch.pipeline()
              .addLast("sslEngine", new SslHandler( newssl(ch)))
              .addLast("spdyframecodec", new SpdyFrameCodec(3))    // TODO: Don't hard code SPDY version
              .addLast("http4s", new NettySpdyServerHandler(service, 3, ch.localAddress(), ch.remoteAddress(), ec))
          }
        })
          .option(ChannelOption.SO_LINGER, new Integer(0))
          .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
          .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
          .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)

        bootstrap.bind(new InetSocketAddress(port)).sync()
          .channel().closeFuture().sync()

      } finally {
        bossThreadPool.shutdownGracefully()
        workerThreadPool.shutdownGracefully()
      }
    }


}
