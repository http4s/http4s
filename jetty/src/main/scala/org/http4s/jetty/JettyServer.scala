package org.http4s
package jetty

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
  host: String,
  port: Int,
  executor: ExecutorService,
  idleTimeout: Duration,
  serviceMounts: Vector[ServiceMount]
) extends ServerBuilder[JettyBuilder] {

  private def copy(host: String = host,
                   port: Int = port,
                   executor: ExecutorService = executor,
                   idleTimeout: Duration = idleTimeout,
                   serviceMounts: Vector[ServiceMount] = serviceMounts): JettyBuilder =
    new JettyBuilder(host, port, executor, idleTimeout, serviceMounts)

  override def withHost(host: String): JettyBuilder = copy(host = host)

  override def withPort(port: Int): JettyBuilder = copy(port = port)

  override def withExecutor(executor: ExecutorService): JettyBuilder = copy(executor = executor)

  override def mountService(service: HttpService, prefix: String): JettyBuilder =
    copy(serviceMounts = serviceMounts :+ ServiceMount(service, prefix))

  override def withIdleTimeout(idleTimeout: Duration): JettyBuilder = copy(idleTimeout = idleTimeout)

  def start: Task[Server] = Task.delay {
    val jetty = new JServer()

    val context = new ServletContextHandler()
    context.setContextPath("/")
    jetty.setHandler(context)

    val connector = new ServerConnector(jetty)
    connector.setHost(host)
    connector.setPort(port)
    connector.setIdleTimeout(if (idleTimeout.isFinite) idleTimeout.toMillis else -1)
    jetty.addConnector(connector)

    for ((serviceMount, i) <- serviceMounts.zipWithIndex) {
      val servlet = new Http4sServlet(
        service = serviceMount.service,
        threadPool = executor
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
  host = "0.0.0.0",
  port = 8080,
  executor = Strategy.DefaultExecutorService,
  idleTimeout = 30.seconds,
  serviceMounts = Vector.empty
)
