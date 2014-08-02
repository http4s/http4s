package org.http4s
package server
package blaze

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.QuietTimeoutStage
import org.http4s.blaze.channel.{SocketConnection, ServerChannel}
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory

import server.middleware.URITranslation

import java.net.InetSocketAddress
import scala.concurrent.duration.Duration
import java.nio.ByteBuffer

import scalaz.concurrent.Task


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

    private var aggregateService = HttpService.empty
    private var port = 8080
    private var idleTimeout: Duration = Duration.Inf

    override def mountService(service: HttpService, prefix: String): this.type = {
      val prefixedService =
        if (prefix.isEmpty) service
        else URITranslation.translateRoot(prefix)(service)
      aggregateService =
        if (aggregateService eq HttpService.empty) prefixedService
        else prefixedService orElse aggregateService
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

    override def build: To = {
      def stage(conn: SocketConnection): LeafBuilder[ByteBuffer] = {
        val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn)))
        if (idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
        else leaf
      }
      val factory = new SocketServerChannelFactory(stage, 12, 8 * 1024)
      val channel = factory.bind(new InetSocketAddress(port))
      new BlazeServer(channel)
    }
  }

  def newBuilder: Builder = new Builder
}
