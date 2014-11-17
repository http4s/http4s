package org.http4s
package tomcat

import java.net.InetSocketAddress

import org.http4s.server.ServerBuilder.ServiceMount
import org.http4s.server._
import org.http4s.servlet.Http4sServlet

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.{Lifecycle, LifecycleEvent, LifecycleListener}
import java.util.concurrent.ExecutorService

sealed class TomcatBuilder (
  socketAddress: InetSocketAddress,
  serviceExecutor: ExecutorService,
  idleTimeout: Duration,
  serviceMounts: Vector[ServiceMount]
) extends ServerBuilder[TomcatBuilder] {
  private def copy(
           socketAddress: InetSocketAddress = socketAddress,
           serviceExecutor: ExecutorService = serviceExecutor,
           idleTimeout: Duration = idleTimeout,
           serviceMounts: Vector[ServiceMount] = serviceMounts): TomcatBuilder =
    new TomcatBuilder(socketAddress, serviceExecutor, idleTimeout, serviceMounts)

  override def withSocketAddress(socketAddress: InetSocketAddress): TomcatBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): TomcatBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def mountService(service: HttpService, prefix: String): TomcatBuilder =
    copy(serviceMounts = serviceMounts :+ ServiceMount(service, prefix))

  override def withIdleTimeout(idleTimeout: Duration): TomcatBuilder = copy(idleTimeout = idleTimeout)

  /**
   * Returns a Task to start a Tomcat server.
   *
   * idleTimeout: Tomcat maintains connections on a fixed interval determined by the global
   * attribute worker.maintain with a default interval of 60 seconds. In the worst case the connection
   * may not timeout for an additional 59.999 seconds from the specified Duration
   */
  override def start: Task[Server] = Task.delay {
    val tomcat = new Tomcat

    tomcat.addContext("", getClass.getResource("/").getPath)
    tomcat.getConnector.setAttribute("address", socketAddress.getHostString)
    tomcat.setPort(socketAddress.getPort)

    tomcat.getConnector.setAttribute("connection_pool_timeout",
      if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0)

    for ((serviceMount, i) <- serviceMounts.zipWithIndex) {
      val servlet = new Http4sServlet(
        service = serviceMount.service,
        threadPool = serviceExecutor
      )
      val wrapper = tomcat.addServlet("", s"servlet-${i}", servlet)
      wrapper.addMapping(s"${serviceMount.prefix}/*")
      wrapper.setAsyncSupported(true)
    }

    tomcat.start()

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        tomcat.stop()
        this
      }

      override def onShutdown(f: => Unit): this.type = {
        tomcat.getServer.addLifecycleListener(new LifecycleListener {
          override def lifecycleEvent(event: LifecycleEvent): Unit = {
            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getLifecycle))
              f
          }
        })
        this
      }
    }
  }
}

object TomcatServer extends TomcatBuilder(
  socketAddress = InetSocketAddress.createUnresolved("0.0.0.0", 8080),
  serviceExecutor = Strategy.DefaultExecutorService,
  idleTimeout = 30.seconds,
  serviceMounts = Vector.empty
)
