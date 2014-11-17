package org.http4s
package jetty

import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.http4s.server.ServerBuilder.ServiceMount
import org.http4s.server._
import org.http4s.servlet.Http4sServlet
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle

sealed class JettyBuilder(
  socketAddress: InetSocketAddress,
  serviceExecutor: ExecutorService,
  idleTimeout: Duration,
  serviceMounts: Vector[ServiceMount]
) extends ServerBuilder[JettyBuilder] {

  private def copy(socketAddress: InetSocketAddress = socketAddress,
                   serviceExecutor: ExecutorService = serviceExecutor,
                   idleTimeout: Duration = idleTimeout,
                   serviceMounts: Vector[ServiceMount] = serviceMounts): JettyBuilder =
    new JettyBuilder(socketAddress, serviceExecutor, idleTimeout, serviceMounts)

  override def withSocketAddress(socketAddress: InetSocketAddress): JettyBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): JettyBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def mountService(service: HttpService, prefix: String): JettyBuilder =
    copy(serviceMounts = serviceMounts :+ ServiceMount(service, prefix))

  override def withIdleTimeout(idleTimeout: Duration): JettyBuilder = copy(idleTimeout = idleTimeout)

  def start: Task[Server] = Task.delay {
    val jetty = new JServer()

    val context = new ServletContextHandler()
    context.setContextPath("/")
    jetty.setHandler(context)

    val connector = new ServerConnector(jetty)
    connector.setHost(socketAddress.getHostString)
    connector.setPort(socketAddress.getPort)
    connector.setIdleTimeout(if (idleTimeout.isFinite) idleTimeout.toMillis else -1)
    jetty.addConnector(connector)

    for ((serviceMount, i) <- serviceMounts.zipWithIndex) {
      val servlet = new Http4sServlet(
        service = serviceMount.service,
        threadPool = serviceExecutor
      )
      val servletName = s"servlet-${i}"
      val urlMapping = s"${serviceMount.prefix}/*"
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    }

    jetty.start()

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        jetty.stop()
        this
      }

      override def onShutdown(f: => Unit): this.type = {
        jetty.addLifeCycleListener { new AbstractLifeCycleListener {
          override def lifeCycleStopped(event: LifeCycle): Unit = f
        }}
        this
      }
    }
  }
}

object JettyServer extends JettyBuilder(
  socketAddress = InetSocketAddress.createUnresolved("0.0.0.0", 8080),
  serviceExecutor = Strategy.DefaultExecutorService,
  idleTimeout = 30.seconds,
  serviceMounts = Vector.empty
)
