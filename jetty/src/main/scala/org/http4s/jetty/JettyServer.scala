package org.http4s
package jetty

import javax.servlet.http.HttpServlet
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.eclipse.jetty.server.{Server => JServer}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import scalaz.\/
import scalaz.concurrent.Task
import org.http4s.servlet.{ServletContainer, ServletContainerBuilder}

class JettyServer private[jetty] (server: JServer) extends ServletContainer with LazyLogging {
  def start: Task[this.type] = Task.async { cb =>
    cb(\/.fromTryCatch(server.start).map(_ => this))
  }

  def shutdown: Task[this.type] = Task.async { cb =>
    cb(\/.fromTryCatch(server.stop).map(_ => this))
  }

  def join(): this.type = {
    server.join()
    this
  }
}

object JettyServer {
  class Builder extends ServletContainerBuilder {
    type To = JettyServer

    private val server = new JServer(8080)

    private val context = new ServletContextHandler()
    context.setContextPath("/")
    server.setHandler(context)

    def build: To = new JettyServer(server)

    def run(): To = build.start.run

    def mountServlet(servlet: HttpServlet, urlMapping: String): this.type = {
      context.addServlet(new ServletHolder(servlet), urlMapping)
      this
    }
  }

  def newBuilder: Builder = new Builder
}
