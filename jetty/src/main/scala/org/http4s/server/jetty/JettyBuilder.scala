package org.http4s
package server
package jetty

import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.http4s.servlet.{ServletContainer, Http4sServlet}
import scala.concurrent.duration._
import scalaz.concurrent.Task
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle

sealed class JettyBuilder private (
  socketAddress: InetSocketAddress,
  private val serviceExecutor: ExecutorService,
  private val idleTimeout: Duration,
  private val asyncTimeout: Duration,
  mounts: Vector[Mount]
)
  extends ServerBuilder
  with ServletContainer
  with IdleTimeoutSupport
{
  type Self = JettyBuilder

  private def copy(socketAddress: InetSocketAddress = socketAddress,
                   serviceExecutor: ExecutorService = serviceExecutor,
                   idleTimeout: Duration = idleTimeout,
                   asyncTimeout: Duration = asyncTimeout,
                   mounts: Vector[Mount] = mounts): JettyBuilder =
    new JettyBuilder(socketAddress, serviceExecutor, idleTimeout, asyncTimeout, mounts)

  override def bindSocketAddress(socketAddress: InetSocketAddress): JettyBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): JettyBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): JettyBuilder =
    copy(mounts = mounts :+ Mount { (context, index, _) =>
      val servletName = name.getOrElse(s"servlet-${index}")
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  override def mountService(service: HttpService, prefix: String): JettyBuilder =
    copy(mounts = mounts :+ Mount { (context, index, builder) =>
      val servlet = new Http4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        threadPool = builder.serviceExecutor
      )
      val servletName = s"servlet-${index}"
      val urlMapping = s"${prefix}/*"
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  override def withIdleTimeout(idleTimeout: Duration): JettyBuilder =
    copy(idleTimeout = idleTimeout)

  override def withAsyncTimeout(asyncTimeout: Duration): JettyBuilder =
    copy(asyncTimeout = asyncTimeout)

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

    for ((mount, i) <- mounts.zipWithIndex)
      mount.f(context, i, this)

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

object JettyBuilder extends JettyBuilder(
  socketAddress = ServerBuilder.DefaultSocketAddress,
  serviceExecutor = ServerBuilder.DefaultServiceExecutor,
  idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
  asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
  mounts = Vector.empty
)

private case class Mount(f: (ServletContextHandler, Int, JettyBuilder) => Unit)
