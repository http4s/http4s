package org.http4s.servlet

import javax.servlet.http.HttpServlet

import org.http4s.server.ServerBuilder

trait ServletContainer { self: ServerBuilder =>
  def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): Self
}

