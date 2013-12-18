
package org.http4s.netty.http

import io.netty.handler.codec.http

import java.net.InetSocketAddress
import java.lang.Boolean

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelOption, ChannelInitializer}
import io.netty.channel.socket.SocketChannel
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s._

import java.util.concurrent.ExecutorService

import scalaz.concurrent.Strategy

object SimpleNettyServer {
  def apply(port: Int = 8080)
           (service: HttpService)
           (implicit es: ExecutorService = Strategy.DefaultExecutorService) =
    new SimpleNettyServer(port, service)
}

class SimpleNettyServer private(port: Int, service: HttpService)
                          (implicit es: ExecutorService) extends Logging {

  private val bossThreadPool = new NioEventLoopGroup()
  private val workerThreadPool = new NioEventLoopGroup()

  def run() {
    try {
      val bootstrap = new ServerBootstrap()
        .group(bossThreadPool, workerThreadPool)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new ChannelInitializer[SocketChannel] {
        def initChannel(ch: SocketChannel) {
          val rem = ch.remoteAddress()
          val local = ch.localAddress()
          logger.trace(s"Started new connection to remote address ${ch.remoteAddress()}")
          ch.pipeline()
            .addLast("httpcodec", new http.HttpServerCodec())    // TODO: set max header sizes etc in the constructor
            .addLast("http4s", new NettyHttpHandler(service, local, rem, es))
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
