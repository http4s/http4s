package org.http4s
package jetty

import javax.servlet.http.HttpServlet
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import scalaz.concurrent.Task
import org.http4s.servlet.{ServletContainer, ServletContainerBuilder}

class JettyServer private[jetty] (server: JServer) extends ServletContainer with LazyLogging {
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
}

object JettyServer {
  class Builder extends ServletContainerBuilder {
    type To = JettyServer

    private val server = new JServer()

    private var port = 8080

    override def withPort(port: Int): this.type = {
      this.port = port
      this
    }

    private val context = new ServletContextHandler()
    context.setContextPath("/")
    server.setHandler(context)

    def build: To = {
      val connector = new ServerConnector(server)
      connector.setPort(port)
      server.addConnector(connector)
      new JettyServer(server)
    }

    def mountServlet(servlet: HttpServlet, urlMapping: String): this.type = {
      context.addServlet(new ServletHolder(servlet), urlMapping)
      this
    }
  }

  def newBuilder: Builder = new Builder
}
