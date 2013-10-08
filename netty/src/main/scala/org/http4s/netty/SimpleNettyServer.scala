
package org.http4s
package netty

//import handlers.StaticFileHandler

import io.netty.handler.codec.http

import java.util.concurrent.{CountDownLatch, Executors}
import java.net.InetSocketAddress
import concurrent.ExecutionContext
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelOption, ChannelInitializer}
import io.netty.channel.socket.SocketChannel
import scalaz.concurrent.Task

object SimpleNettyServer {
  def apply(port: Int = 8080, staticFiles: String = "src/main/webapp")(service: HttpService) =
    new SimpleNettyServer(port, staticFiles, service)
}

class SimpleNettyServer private(port: Int, staticFiles: String, service: HttpService)(implicit executionContext: ExecutionContext = ExecutionContext.global) {

  private val bossThreadPool = new NioEventLoopGroup()
  private val workerThreadPool = new NioEventLoopGroup()

  def run() {
    try {
      val bootstrap = new ServerBootstrap()
        .group(bossThreadPool, workerThreadPool)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new ChannelInitializer[SocketChannel] {
        def initChannel(ch: SocketChannel) {
          ch.pipeline()
            .addLast("httpcodec", new http.HttpServerCodec())    // TODO: set max header sizes etc in the constructor
            .addLast("http4s", Http4sNetty(service))
        }
      })
        .option(ChannelOption.SO_LINGER, new Integer(0))
        .option(ChannelOption.SO_REUSEADDR, new java.lang.Boolean(true))
        .option(ChannelOption.SO_KEEPALIVE, new java.lang.Boolean(true))
        .option(ChannelOption.SO_KEEPALIVE, new java.lang.Boolean(true))

      bootstrap.bind(new InetSocketAddress(port)).sync()
        .channel().closeFuture().sync()

    } finally {
      bossThreadPool.shutdownGracefully()
      workerThreadPool.shutdownGracefully()
    }
  }
}
