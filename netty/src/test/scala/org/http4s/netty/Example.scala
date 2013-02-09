package org.http4s
package netty

import play.api.libs.iteratee.{Concurrent, Done}
import org.http4s._
import Bodies._
import org.jboss.netty.channel.{Channels, ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import java.net.InetSocketAddress
import java.util.concurrent.{CountDownLatch, Executors}
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import com.typesafe.scalalogging.slf4j.Logging

object Example extends App with Logging {

  def routeHandler = Routes {
    case req if req.pathInfo == "/ping" =>
      logger.info("Got a ping request")
      Done(Responder(body = "pong", headers = Headers(Header("Content-Length", "4"))))

    case req if req.pathInfo == "/stream" =>
      Done(Responder(body = Concurrent unicast { channel =>
          for (i <- 1 to 10) {
            channel.push(s"$i".getBytes))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      }))

    case req if req.pathInfo == "/echo" =>
      Done(Responder(body = req.body))
  }

  val channelFactory = new ChannelPipelineFactory {
    def getPipeline: ChannelPipeline = {
      val pipe = Channels.pipeline()
      pipe.addLast("decoder", new HttpRequestDecoder)
      pipe.addLast("encoder", new HttpResponseEncoder)
      pipe.addLast("chunkedWriter", new ChunkedWriteHandler)
      pipe.addLast("route", routeHandler)
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

  bootstrap setPipelineFactory channelFactory
  bootstrap.bind(new InetSocketAddress(8080))

  val latch = new CountDownLatch(1)

  sys addShutdownHook {
    bootstrap.releaseExternalResources()
    workerThreadPool.shutdown()
    bossThreadPool.shutdown()
    latch.countDown()
  }

  latch.await()

}