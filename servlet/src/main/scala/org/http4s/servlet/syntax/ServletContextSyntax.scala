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
package syntax

import cats.effect._
import cats.effect.std.Dispatcher
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.server.defaults
import org.http4s.syntax.all._

import javax.servlet.ServletContext
import javax.servlet.ServletRegistration
import scala.concurrent.duration.Duration

trait ServletContextSyntax {
  implicit def ToServletContextOps(self: ServletContext): ServletContextOps =
    new ServletContextOps(self)
}

final class ServletContextOps private[syntax] (val self: ServletContext) extends AnyVal {

  /** Wraps an [[HttpRoutes]] and mounts it as an [[AsyncHttp4sServlet]]
    *
    * Assumes non-blocking servlet IO is available, and thus requires at least Servlet 3.1.
    */
  @deprecated("Use mountRoutes instead", "0.23.11")
  def mountService[F[_]: Async](
      name: String,
      service: HttpRoutes[F],
      mapping: String = "/*",
      dispatcher: Dispatcher[F],
  ): ServletRegistration.Dynamic =
    mountHttpApp(name, service.orNotFound, mapping, dispatcher, defaults.ResponseTimeout)

  def mountRoutes[F[_]: Async](
      name: String,
      service: HttpRoutes[F],
      mapping: String = "/*",
      dispatcher: Dispatcher[F],
      asyncTimeout: Duration = defaults.ResponseTimeout,
  ): ServletRegistration.Dynamic =
    mountHttpApp(name, service.orNotFound, mapping, dispatcher, asyncTimeout)

  @deprecated("Use mountHttpApp with async timeout param instead", "0.23.11")
  private[servlet] def mountHttpApp[F[_]: Async](
      name: String,
      service: HttpApp[F],
      mapping: String,
      dispatcher: Dispatcher[F],
  ): ServletRegistration.Dynamic =
    mountHttpApp(name, service, mapping, dispatcher, defaults.ResponseTimeout)

  def mountHttpApp[F[_]: Async](
      name: String,
      service: HttpApp[F],
      mapping: String = "/*",
      dispatcher: Dispatcher[F],
      asyncTimeout: Duration = defaults.ResponseTimeout,
  ): ServletRegistration.Dynamic = {
    val servlet = new AsyncHttp4sServlet(
      service = service,
      asyncTimeout = asyncTimeout,
      servletIo = NonBlockingServletIo(DefaultChunkSize),
      serviceErrorHandler = DefaultServiceErrorHandler[F],
      dispatcher,
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }
}

object ServletContextOps extends ServletContextOpsCompanionCompat

object servletContext extends ServletContextSyntax
