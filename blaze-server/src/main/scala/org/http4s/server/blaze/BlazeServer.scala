package org.http4s
package server
package blaze

import java.util.concurrent.ExecutorService

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.QuietTimeoutStage
import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerChannelFactory
import org.http4s.blaze.channel.nio2.NIO2SocketServerChannelFactory
import org.http4s.server.ServerBuilder.ServiceMount

import server.middleware.URITranslation

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

class BlazeBuilder(
  host: String,
  port: Int,
  executor: ExecutorService,
  idleTimeout: Duration,
  isNio2: Boolean,
  serviceMounts: Vector[ServiceMount]
) extends ServerBuilder[BlazeBuilder] {
  private def copy(host: String = host,
                   port: Int = port,
                   executor: ExecutorService = executor,
                   idleTimeout: Duration = idleTimeout,
                   isNio2: Boolean = isNio2,
                   serviceMounts: Vector[ServiceMount] = serviceMounts): BlazeBuilder =
    new BlazeBuilder(host, port, executor, idleTimeout, isNio2, serviceMounts)

  override def withHost(host: String): BlazeBuilder = copy(host = host)

  override def withPort(port: Int): BlazeBuilder = copy(port = port)

  override def withExecutor(executor: ExecutorService): BlazeBuilder = copy(executor = executor)

  override def withIdleTimeout(idleTimeout: Duration): BlazeBuilder = copy(idleTimeout = idleTimeout)

  def withNio2(isNio2: Boolean): BlazeBuilder = copy(isNio2 = isNio2)

  override def mountService(service: HttpService, prefix: String): BlazeBuilder =
    copy(serviceMounts = serviceMounts :+ ServiceMount(service, prefix))


  def start: Task[Server] = Task.delay {
    val aggregateService = serviceMounts.foldLeft[HttpService](Service.empty) {
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
      val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), executor))
      if (idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
      else leaf
    }

    val factory =
      if (isNio2)
        new NIO2SocketServerChannelFactory(pipelineFactory)
      else
        new NIO1SocketServerChannelFactory(pipelineFactory, 12, 8 * 1024)

    val address = new InetSocketAddress(host, port)
    if (address.isUnresolved) throw new Exception(s"Unresolved hostname: ${host}")

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

object BlazeServer extends BlazeBuilder(
  host = "0.0.0.0",
  port = 8080,
  executor = Strategy.DefaultExecutorService,
  idleTimeout = 30.seconds,
  isNio2 = true,
  serviceMounts = Vector.empty
)

