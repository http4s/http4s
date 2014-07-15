package org.http4s
package servlet

import org.http4s.server.{HttpService, HasAsyncTimeout, ServerBuilder, Server}

import javax.servlet.http.HttpServlet

import scala.concurrent.duration.Duration

trait ServletContainer extends Server

trait ServletContainerBuilder extends ServerBuilder with HasAsyncTimeout {
  type To <: ServletContainer

  private var asyncTimeout: Duration = Duration.Inf

  protected def defaultServletName(servlet: HttpServlet): String =
    s"${servlet.getClass.getName}-${System.identityHashCode(servlet)}"

  def mountService(service: HttpService, prefix: String): this.type = {
    val pathMapping = s"${prefix}/*"
    mountServlet(new Http4sServlet(service, asyncTimeout), pathMapping)
  }

  def mountServlet(servlet: HttpServlet, urlMapping: String): this.type

  override def withAsyncTimeout(asyncTimeout: Duration): this.type = {
    this.asyncTimeout = asyncTimeout
    this
  }
}
