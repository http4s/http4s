package org.http4s
package blaze

import java.net.InetSocketAddress
import org.http4s.blaze.channel.ServerChannel
import org.http4s.server.{ServerBuilder, Server}
import scalaz.\/
import scalaz.concurrent.Task
import org.http4s.middleware.URITranslation
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.LeafBuilder

class BlazeServer private (serverChannel: ServerChannel) extends Server {
  override def start: Task[this.type] = Task.async { cb =>
    cb(\/.fromTryCatch(serverChannel.run()).map(_ => this))
  }

  override def shutdown: Task[this.type] = Task.async { cb =>
    cb(\/.fromTryCatch(serverChannel.close()).map(_ => this))
  }
}

object BlazeServer {
  class Builder extends ServerBuilder {
    type To = BlazeServer

    private var aggregateService = HttpService.empty

    override def mountService(service: HttpService, prefix: String): this.type = {
      aggregateService = URITranslation.translateRoot(prefix)(service) orElse service
      this
    }

    override def build: To = {
      def stage(): LeafBuilder[ByteBuffer] = new Http1Stage(aggregateService)
      val factory = new SocketServerChannelFactory(stage, 12, 8 * 1024)
      val channel = factory.bind(new InetSocketAddress(8080))
      new BlazeServer(channel)
    }
  }

  def newBuilder: Builder = new Builder
}
