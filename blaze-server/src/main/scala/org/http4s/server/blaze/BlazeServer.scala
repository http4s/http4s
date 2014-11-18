package org.http4s
package server
package blaze

import java.util.concurrent.ExecutorService

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.QuietTimeoutStage
import org.http4s.blaze.channel.{SocketConnection, ServerChannel}
import org.http4s.blaze.channel.nio1.NIO1SocketServerChannelFactory
import org.http4s.blaze.channel.nio2.NIO2SocketServerChannelFactory

import server.middleware.URITranslation

import java.net.InetSocketAddress
import scala.concurrent.duration.Duration
import java.nio.ByteBuffer

import scalaz.concurrent.{Strategy, Task}


class BlazeServer private (serverChannel: ServerChannel) extends Server {
  override def start: Task[this.type] = Task.delay {
    serverChannel.run()
    this
  }

  override def shutdown: Task[this.type] = Task.delay {
    serverChannel.close()
    this
  }

  override def onShutdown(f: => Unit): this.type = {
    serverChannel.addShutdownHook(() => f)
    this
  }
}

object BlazeServer {
  class Builder extends ServerBuilder with HasIdleTimeout {
    type To = BlazeServer

    private var aggregateService = Service.empty[Request, Response]
    private var port = 8080
    private var idleTimeout: Duration = Duration.Inf
    private var host = "0.0.0.0"
    private var isnio2 = false
    private var threadPool: ExecutorService = Strategy.DefaultExecutorService

    override def mountService(service: HttpService, prefix: String): this.type = {
      val prefixedService =
        if (prefix.isEmpty || prefix == "/") service
        else URITranslation.translateRoot(prefix)(service)
      aggregateService =
        if (aggregateService.run eq Service.empty.run) prefixedService
        else prefixedService orElse aggregateService
      this
    }


    override def withHost(host: String): this.type = {
      this.host = host
      this
    }

    override def withPort(port: Int): this.type = {
      this.port = port
      this
    }

    override def withIdleTimeout(timeout: Duration): this.type = {
      this.idleTimeout = idleTimeout
      this
    }

    def withNIO2(usenio2: Boolean): this.type = {
      this.isnio2 = usenio2
      this
    }

    override def withThreadPool(pool: ExecutorService): this.type = {
      this.threadPool = pool
      this
    }

    override def build: To = {
      def pipelineFactory(conn: SocketConnection): LeafBuilder[ByteBuffer] = {
        val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), threadPool))
        if (idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
        else leaf
      }

      val factory = if (isnio2) new NIO2SocketServerChannelFactory(pipelineFactory)
                    else new NIO1SocketServerChannelFactory(pipelineFactory, 12, 8 * 1024)

      val address = new InetSocketAddress(host, port)
      if (address.isUnresolved) throw new Exception(s"Unresolved hostname: $host")

      val channel = factory.bind(address)
      new BlazeServer(channel)
    }
  }

  def newBuilder: Builder = new Builder
}
