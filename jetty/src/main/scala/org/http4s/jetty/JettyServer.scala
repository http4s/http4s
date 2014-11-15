package org.http4s
package jetty

import java.util.concurrent.atomic.AtomicInteger
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.http4s.server._
import org.http4s.servlet.Http4sServlet
import scalaz.concurrent.Task
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle

object JettyServer extends ServerBackend {
  def apply(config: ServerConfig): Task[Server] = Task.delay {
    val jetty = new JServer()

    val context = new ServletContextHandler()
    context.setContextPath("/")
    jetty.setHandler(context)

    val connector = new ServerConnector(jetty)
    connector.setHost(config.host)
    connector.setPort(config.port)
    val idleTimeout = if (config.idleTimeout.isFinite) config.idleTimeout.toMillis else -1
    connector.setIdleTimeout(idleTimeout)
    jetty.addConnector(connector)

    val nameCounter = new AtomicInteger
    for (serviceMount <- config.serviceMounts) {
      val servlet = new Http4sServlet(
        service = serviceMount.service,
        threadPool = config.executor
      )
      val servletName = s"servlet-${nameCounter.getAndIncrement}"
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
