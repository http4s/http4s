package org.http4s
package servlet

import org.http4s.server.{ServerBuilder, Server}
import javax.servlet.http.HttpServlet
import javax.servlet.ServletContext

trait ServletContainer extends Server

trait ServletContainerBuilder extends ServerBuilder {
  type To <: ServletContainer

  protected def defaultServletName(servlet: HttpServlet): String =
    s"${servlet.getClass.getName}-${System.identityHashCode(servlet)}"

  def mountService(service: HttpService, prefix: String): this.type = {
    val pathMapping = s"${prefix}/*"
    mountServlet(new Http4sServlet(service), pathMapping)
  }

  def mountServlet(servlet: HttpServlet, urlMapping: String): this.type
}
