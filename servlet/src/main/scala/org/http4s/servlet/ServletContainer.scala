package org.http4s.servlet

import javax.servlet.http.HttpServlet

import org.http4s.server.{AsyncTimeoutSupport, ServerBuilder}

trait ServletContainer
  extends ServerBuilder
  with AsyncTimeoutSupport
{
  type Self <: ServletContainer

  def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): Self

  /**
   * Sets the servlet I/O mode for reads and writes within the servlet.
   * Not to be confused with the server connectors.
   *
   * @see [[ServletIo]]
   */
  def withServletIo(servletIo: ServletIo): Self
}

object ServletContainer {
  val DefaultServletIo = NonBlockingServletIo(4096)
}

