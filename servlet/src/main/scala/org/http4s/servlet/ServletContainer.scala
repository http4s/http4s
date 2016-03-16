package org.http4s
package servlet

import java.util.EnumSet
import javax.servlet.{DispatcherType, Filter}
import javax.servlet.http.HttpServlet

import org.http4s.server.{AsyncTimeoutSupport, ServerBuilder}

trait ServletContainer
  extends ServerBuilder
  with AsyncTimeoutSupport
{
  type Self <: ServletContainer

  /**
    * Mounts a servlet to the server.
    *
    * The http4s way is to create an [[HttpService]], which runs not just on servlet containers,
    * but all supported backends.  This method is good for legacy scenarios, or for reusing parts
    * of the servlet ecosystem for an app that is committed to running on a servlet container.
    */
  def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): Self

  /**
    * Mounts a filter to the server.
    *
    * The http4s way is to create a middleware around an  [[HttpService]], which runs not just on
    * servlet containers, but all supported backends.  This method is good for legacy scenarios,
    * or for reusing parts of the servlet ecosystem for an app that is committed to running on
    * a servlet container.
    */
  def mountFilter(filter: Filter,
                  urlMapping: String,
                  name: Option[String] = None,
                  dispatches: EnumSet[DispatcherType] = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE, DispatcherType.ASYNC)): Self

  /**
   * Sets the servlet I/O mode for reads and writes within the servlet.
   * Not to be confused with the server connectors.
   *
   * @see [[ServletIo]]
   */
  def withServletIo(servletIo: ServletIo): Self
}

object ServletContainer {
  val DefaultServletIo = NonBlockingServletIo(DefaultChunkSize)

  /**
   * Trims an optional trailing slash and then appends "/\u002b'.  Translates an argument to
   * mountService into a standard servlet prefix mapping.
   */
  def prefixMapping(prefix: String) = prefix.replaceAll("/?$", "") + "/*"
}

