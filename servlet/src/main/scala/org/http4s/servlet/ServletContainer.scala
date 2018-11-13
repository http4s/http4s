package org.http4s
package servlet

import cats.effect._
import java.util
import javax.servlet.{DispatcherType, Filter}
import javax.servlet.http.HttpServlet
import org.http4s.server.ServerBuilder

abstract class ServletContainer[F[_]: Async] extends ServerBuilder[F] {
  type Self <: ServletContainer[F]

  /**
    * Mounts a servlet to the server.
    *
    * The http4s way is to create [[HttpRoutes]], which runs not just on servlet containers,
    * but all supported backends.  This method is good for legacy scenarios, or for reusing parts
    * of the servlet ecosystem for an app that is committed to running on a servlet container.
    */
  def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): Self

  /**
    * Mounts a filter to the server.
    *
    * The http4s way is to create a middleware around an [[HttpRoutes]], which runs not just on
    * servlet containers, but all supported backends.  This method is good for legacy scenarios,
    * or for reusing parts of the servlet ecosystem for an app that is committed to running on
    * a servlet container.
    */
  def mountFilter(
      filter: Filter,
      urlMapping: String,
      name: Option[String] = None,
      dispatches: util.EnumSet[DispatcherType] = util.EnumSet.of(
        DispatcherType.REQUEST,
        DispatcherType.FORWARD,
        DispatcherType.INCLUDE,
        DispatcherType.ASYNC)): Self

  /**
    * Sets the servlet I/O mode for reads and writes within the servlet.
    * Not to be confused with the server connectors.
    *
    * @see [[org.http4s.servlet.ServletIo]]
    */
  def withServletIo(servletIo: ServletIo[F]): Self
}

object ServletContainer {
  def DefaultServletIo[F[_]: Effect]: ServletIo[F] = NonBlockingServletIo[F](DefaultChunkSize)

  /**
    * Trims an optional trailing slash and then appends "/\u002b'.  Translates an argument to
    * mountService into a standard servlet prefix mapping.
    */
  def prefixMapping(prefix: String): String = prefix.replaceAll("/?$", "") + "/*"
}
