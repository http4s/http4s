package org.http4s
package netty4

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

object SimpleNettyServer {
  def apply(contextRoot: String, port: Int = 8080, staticFiles: String = "src/main/webapp")(route: Route)(implicit executionContext: ExecutionContext = ExecutionContext.global) =
    new SimpleNettyServer(contextRoot, port, staticFiles, Seq(route))
}

class SimpleNettyServer private(contextRoot: String, port: Int, staticFiles: String, routes: Seq[Route])(implicit executionContext: ExecutionContext = ExecutionContext.global) {
//
//  private val channelFactory = new ChannelPipelineFactory {
//    def getPipeline: ChannelPipeline = {
//      val pipe = Channels.pipeline()
//      pipe.addLast("decoder", new http.HttpRequestDecoder)
//      pipe.addLast("encoder", new HttpResponseEncoder)
//      pipe.addLast("chunkedWriter", new ChunkedWriteHandler)
//      pipe.addLast("route", Http4sNetty(routes reduce (_ orElse _), contextRoot))
//      pipe.addLast("staticFiles", new StaticFileHandler(staticFiles))
//      pipe
//    }
//  }

  private val bossThreadPool = new NioEventLoopGroup()
  private val workerThreadPool = new NioEventLoopGroup()

  private val bootstrap = new ServerBootstrap()
    .group(bossThreadPool, workerThreadPool)
    .channel(classOf[NioServerSocketChannel])
    .childHandler(new ChannelInitializer[SocketChannel] {
      def initChannel(ch: SocketChannel) {
        ch.pipeline()
          .addLast("decoder", new http.HttpRequestDecoder())    // TODO: set max header sizes etc in the constructor
          .addLast("encoder", new http.HttpResponseEncoder())
          .addLast("http4s", Http4sNetty(routes.reduce(_ orElse _), contextRoot))
      }
    })
    .option(ChannelOption.SO_LINGER, new Integer(0))
    .option(ChannelOption.SO_REUSEADDR, new java.lang.Boolean(true))
    .option(ChannelOption.SO_KEEPALIVE, new java.lang.Boolean(true))
    .option(ChannelOption.SO_KEEPALIVE, new java.lang.Boolean(true))

  bootstrap.bind(new InetSocketAddress(port)).sync()
//
//  val latch = new CountDownLatch(1)
//
//  sys addShutdownHook {
//    bootstrap.releaseExternalResources()
//    workerThreadPool.shutdown()
//    bossThreadPool.shutdown()
//    latch.countDown()
//  }
//
//
//  latch.await()
}