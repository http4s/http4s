/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package servlet

import cats.effect._
import java.util
import javax.servlet.{DispatcherType, Filter}
import javax.servlet.http.HttpServlet
import org.http4s.server.ServerBuilder

abstract class ServletContainer[F[_]: Async] extends ServerBuilder[F] {
  type Self <: ServletContainer[F]

  /** Mounts a servlet to the server.
    *
    * The http4s way is to create [[HttpRoutes]], which runs not just on servlet containers,
    * but all supported backends.  This method is good for legacy scenarios, or for reusing parts
    * of the servlet ecosystem for an app that is committed to running on a servlet container.
    */
  def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): Self

  /** Mounts a filter to the server.
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

  /** Sets the servlet I/O mode for reads and writes within the servlet.
    * Not to be confused with the server connectors.
    *
    * @see [[org.http4s.servlet.ServletIo]]
    */
  def withServletIo(servletIo: ServletIo[F]): Self
}

object ServletContainer {
  def DefaultServletIo[F[_]: Async]: ServletIo[F] = NonBlockingServletIo[F](DefaultChunkSize)

  /** Trims an optional trailing slash and then appends "/\u002b'.  Translates an argument to
    * mountService into a standard servlet prefix mapping.
    */
  def prefixMapping(prefix: String): String = prefix.replaceAll("/?$", "") + "/*"
}
