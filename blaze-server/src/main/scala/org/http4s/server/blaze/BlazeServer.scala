package org.http4s
package server
package blaze

import java.util.concurrent.ExecutorService

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.QuietTimeoutStage
import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerChannelFactory
import org.http4s.blaze.channel.nio2.NIO2SocketServerChannelFactory

import server.middleware.URITranslation

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

class BlazeBuilder(
  socketAddress: InetSocketAddress,
  serviceExecutor: ExecutorService,
  idleTimeout: Duration,
  isNio2: Boolean,
  serviceMounts: Vector[ServiceMount]
)
  extends ServerBuilder
  with IdleTimeoutSupport
{
  type Self = BlazeBuilder

  private def copy(socketAddress: InetSocketAddress = socketAddress,
                   serviceExecutor: ExecutorService = serviceExecutor,
                   idleTimeout: Duration = idleTimeout,
                   isNio2: Boolean = isNio2,
                   serviceMounts: Vector[ServiceMount] = serviceMounts): BlazeBuilder =
    new BlazeBuilder(socketAddress, serviceExecutor, idleTimeout, isNio2, serviceMounts)

  override def bindSocketAddress(socketAddress: InetSocketAddress): BlazeBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): BlazeBuilder =
    copy(serviceExecutor = serviceExecutor)

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
      val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), serviceExecutor))
      if (idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
      else leaf
    }

    val factory =
      if (isNio2)
        new NIO2SocketServerChannelFactory(pipelineFactory)
      else
        new NIO1SocketServerChannelFactory(pipelineFactory, 12, 8 * 1024)

    val serverChannel = factory.bind(socketAddress)
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

object BlazeBuilder extends BlazeBuilder(
  socketAddress = InetSocketAddress.createUnresolved("0.0.0.0", 8080),
  serviceExecutor = Strategy.DefaultExecutorService,
  idleTimeout = 30.seconds,
  isNio2 = true,
  serviceMounts = Vector.empty
)

private case class ServiceMount(service: HttpService, prefix: String)

