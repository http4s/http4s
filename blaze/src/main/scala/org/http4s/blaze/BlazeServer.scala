package org.http4s
package blaze

import java.net.InetSocketAddress
import org.http4s.blaze.channel.{SocketConnection, ServerChannel}
import org.http4s.server.{ServerBuilder, Server}
import scalaz.concurrent.Task
import org.http4s.middleware.URITranslation
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.LeafBuilder

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
  class Builder extends ServerBuilder {
    type To = BlazeServer

    private var aggregateService = HttpService.empty
    private var port = 8080

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

    override def build: To = {
      def stage(conn: SocketConnection): LeafBuilder[ByteBuffer] = new Http1Stage(aggregateService, Some(conn))
      val factory = new SocketServerChannelFactory(stage, 12, 8 * 1024)
      val channel = factory.bind(new InetSocketAddress(port))
      new BlazeServer(channel)
    }
  }

  def newBuilder: Builder = new Builder
}
