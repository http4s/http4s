package org.http4s
package netty3

import handlers.StaticFileHandler
import org.jboss.netty.channel.{Channels, ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http.{HttpResponseEncoder, HttpRequestDecoder}
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import java.util.concurrent.{CountDownLatch, Executors}
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import concurrent.ExecutionContext

object SimpleNettyServer {
  def apply(contextRoot: String, port: Int = 8080, staticFiles: String = "src/main/webapp")(route: Route)(implicit executionContext: ExecutionContext = ExecutionContext.global) =
    new SimpleNettyServer(contextRoot, port, staticFiles, Seq(route))
}

class SimpleNettyServer private(contextRoot: String, port: Int, staticFiles: String, routes: Seq[Route])(implicit executionContext: ExecutionContext = ExecutionContext.global) {

  private val channelFactory = new ChannelPipelineFactory {
    def getPipeline: ChannelPipeline = {
      val pipe = Channels.pipeline()
      pipe.addLast("decoder", new HttpRequestDecoder)
      pipe.addLast("encoder", new HttpResponseEncoder)
      pipe.addLast("chunkedWriter", new ChunkedWriteHandler)
      pipe.addLast("route", Http4sNetty(routes reduce (_ orElse _), contextRoot))
      pipe.addLast("staticFiles", new StaticFileHandler(staticFiles))
      pipe
    }
  }

  private val bossThreadPool = Executors.newCachedThreadPool()
  private val workerThreadPool = Executors.newCachedThreadPool()

  private val bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(bossThreadPool, workerThreadPool))
  bootstrap.setOption("soLinger", 0)
  bootstrap.setOption("reuseAddress", true)
  bootstrap.setOption("child.tcpNoDelay", true)
  bootstrap.setOption("child.keepAlive", true)

  bootstrap.setPipelineFactory(channelFactory)
  bootstrap.bind(new InetSocketAddress(port))

  val latch = new CountDownLatch(1)

  sys addShutdownHook {
    bootstrap.releaseExternalResources()
    workerThreadPool.shutdown()
    bossThreadPool.shutdown()
    latch.countDown()
  }


  latch.await()
}