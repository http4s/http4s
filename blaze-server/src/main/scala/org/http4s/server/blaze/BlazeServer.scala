package org.http4s
package server
package blaze

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.QuietTimeoutStage
import org.http4s.blaze.channel.{SocketConnection, ServerChannel}
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import org.http4s.blaze.channel.nio2.NIO2ServerChannelFactory
import org.http4s.server.ServerConfig.ServiceMount

import server.middleware.URITranslation

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import scalaz.concurrent.Task

object BlazeServer extends ServerBackend {
  object keys {
    val isNio2 = AttributeKey[Boolean]("org.http4s.server.blaze.config.isNio2")
  }

  implicit class BlazeServerConfigSyntax(config: ServerConfig) {
    def isNio2: Boolean = config.getOrElse(keys.isNio2, false)
    def withNio2(nio2: Boolean) = config.put(keys.isNio2, nio2)
  }

  def apply(config: ServerConfig): Task[Server] = Task.delay {
    val aggregateService = config.serviceMounts.foldLeft[HttpService](Service.empty) {
      case (aggregate, ServiceMount(service, prefix)) =>
        val prefixedService =
          if (prefix.isEmpty || prefix == "/") service
          else URITranslation.translateRoot(prefix)(service)

        if (aggregate.run eq Service.empty.run)
          prefixedService
        else
          prefixedService orElse aggregate
    }

    def pipelineFactory(conn: SocketConnection): LeafBuilder[ByteBuffer] = {
      val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), config.executor))
      if (config.idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](config.idleTimeout))
      else leaf
    }

    val factory =
      if (config.isNio2)
        new NIO2ServerChannelFactory(pipelineFactory)
      else
        new SocketServerChannelFactory(pipelineFactory, 12, 8 * 1024)

    val address = new InetSocketAddress(config.host, config.port)
    if (address.isUnresolved) throw new Exception(s"Unresolved hostname: ${config.host}")

    val serverChannel = factory.bind(address)
    serverChannel.run()

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        serverChannel.close()
        this
      }

      override def onShutdown(f: => Unit): this.type = {
        serverChannel.addShutdownHook(() => f)
        this
      }
    }
  }
}
