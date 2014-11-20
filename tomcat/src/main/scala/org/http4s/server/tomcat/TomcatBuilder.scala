package org.http4s
package server
package tomcat

import java.net.InetSocketAddress
import javax.servlet.http.HttpServlet

import org.http4s.servlet.{ServletContainer, Http4sServlet}

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.{Lifecycle, LifecycleEvent, LifecycleListener}
import java.util.concurrent.ExecutorService

sealed class TomcatBuilder (
  socketAddress: InetSocketAddress,
  serviceExecutor: ExecutorService,
  idleTimeout: Duration,
  mounts: Vector[Mount]
)
  extends ServerBuilder
  with ServletContainer
  with IdleTimeoutSupport
{
  type Self = TomcatBuilder

  private def copy(
           socketAddress: InetSocketAddress = socketAddress,
           serviceExecutor: ExecutorService = serviceExecutor,
           idleTimeout: Duration = idleTimeout,
           mounts: Vector[Mount] = mounts): TomcatBuilder =
    new TomcatBuilder(socketAddress, serviceExecutor, idleTimeout, mounts)

  override def bindSocketAddress(socketAddress: InetSocketAddress): TomcatBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): TomcatBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): TomcatBuilder =
    copy(mounts = mounts :+ Mount { (tomcat, index, _) =>
      val servletName = name.getOrElse(s"servlet-$index")
      val wrapper = tomcat.addServlet("", servletName, servlet)
      wrapper.addMapping(urlMapping)
      wrapper.setAsyncSupported(true)
    })

  override def mountService(service: HttpService, prefix: String): TomcatBuilder =
    copy(mounts = mounts :+ Mount { (tomcat, index, serviceExecutor) =>
      val servlet = new Http4sServlet(
        service = service,
        threadPool = serviceExecutor
      )
      val wrapper = tomcat.addServlet("", s"servlet-$index", servlet)
      wrapper.addMapping(s"$prefix/*")
      wrapper.setAsyncSupported(true)
    })

  /*
   * Tomcat maintains connections on a fixed interval determined by the global
   * attribute worker.maintain with a default interval of 60 seconds. In the worst case the connection
   * may not timeout for an additional 59.999 seconds from the specified Duration
   */
  override def withIdleTimeout(idleTimeout: Duration): TomcatBuilder =
    copy(idleTimeout = idleTimeout)

  override def start: Task[Server] = Task.delay {
    val tomcat = new Tomcat

    tomcat.addContext("", getClass.getResource("/").getPath)
    tomcat.getConnector.setAttribute("address", socketAddress.getHostString)
    tomcat.setPort(socketAddress.getPort)

    tomcat.getConnector.setAttribute("connection_pool_timeout",
      if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0)

    for ((mount, i) <- mounts.zipWithIndex)
      mount.f(tomcat, i, serviceExecutor)

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

object TomcatBuilder extends TomcatBuilder(
  socketAddress = ServerBuilder.DefaultSocketAddress,
  serviceExecutor = Strategy.DefaultExecutorService,
  idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
  mounts = Vector.empty
)

private case class Mount(f: (Tomcat, Int, ExecutorService) => Unit)

