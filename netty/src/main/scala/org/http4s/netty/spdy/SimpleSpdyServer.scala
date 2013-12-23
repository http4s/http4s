package org.http4s.netty.spdy

import javax.net.ssl.SSLContext
import java.lang.Boolean
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService

import io.netty.channel.{ChannelOption, ChannelInitializer}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslHandler
import org.eclipse.jetty.npn.NextProtoNego


import com.typesafe.scalalogging.slf4j.Logging

import org.http4s._
import org.http4s.netty.NPNProvider

import scalaz.concurrent.Strategy

/**
* @author Bryce Anderson
*         Created on 11/28/13
*/

object SimpleSpdyServer {
  def apply(sslContext: SSLContext, port: Int = 443)
           (service: HttpService)(implicit es: ExecutorService = Strategy.DefaultExecutorService) =
    new SimpleSpdyServer(sslContext, port, service)
}

class SimpleSpdyServer(sslContext: SSLContext, port: Int, service: HttpService)
                      (implicit es: ExecutorService) extends Logging {

    private val bossThreadPool = new NioEventLoopGroup()
    private val workerThreadPool = new NioEventLoopGroup()

    private def newssl() = {
      val eng = sslContext.createSSLEngine()
      eng.setUseClientMode(false)
      val p = new NPNProvider
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
            logger.trace(s"Creating new channel. Local: ${ch.localAddress}, remote: ${ch.remoteAddress}")
            ch.pipeline()
              .addLast("sslEngine", new SslHandler( newssl))
              .addLast("chooser", new NettySpdyChooser(service))
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
