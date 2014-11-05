package org.http4s
package jetty

import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.http4s.server.HasIdleTimeout
import org.log4s.getLogger
import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import org.http4s.servlet.{ServletContainer, ServletContainerBuilder}
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle

class JettyServer private[jetty] (server: JServer) extends ServletContainer {
  private[this] val logger = getLogger

  def start: Task[this.type] = Task.delay {
    server.start()
    this
  }

  def shutdown: Task[this.type] = Task.delay {
    server.stop()
    this
  }

  def join(): this.type = {
    server.join()
    this
  }

  override def onShutdown(f: => Unit): this.type = {
    server.addLifeCycleListener { new AbstractLifeCycleListener {
      override def lifeCycleStopped(event: LifeCycle): Unit = f
    }}
    this
  }
}

object JettyServer {
  class Builder extends ServletContainerBuilder with HasIdleTimeout {
    type To = JettyServer

    private val server = new JServer()
    private var port = 8080
    private var host = "0.0.0.0"
    private var timeout: Duration = Duration.Inf

    override def withPort(port: Int): this.type = {
      this.port = port
      this
    }

    override def withHost(host: String): this.type = {
      this.host = host
      this
    }

    private val context = new ServletContextHandler()
    context.setContextPath("/")
    server.setHandler(context)

    def build: To = {
      val connector = new ServerConnector(server)
      connector.setHost(host)
      connector.setPort(port)
      server.addConnector(connector)
      val dur = if (timeout.isFinite) timeout.toMillis else -1
      connector.setIdleTimeout(dur)  // timeout <= 0 => infinite
      new JettyServer(server)
    }

    def mountServlet(servlet: HttpServlet, urlMapping: String): this.type = {
      context.addServlet(new ServletHolder(defaultServletName(servlet), servlet), urlMapping)
      this
    }

    def withIdleTimeout(timeout: Duration): this.type = {
      this.timeout = timeout
      this
    }
  }

  def newBuilder: Builder = new Builder
}
