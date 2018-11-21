package org.http4s
package servlet
package syntax

import cats.effect._
import javax.servlet.{ServletContext, ServletRegistration}
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.server.defaults

trait ServletContextSyntax {
  implicit def ToServletContextOps(self: ServletContext): ServletContextOps =
    new ServletContextOps(self)
}

final class ServletContextOps private[syntax] (val self: ServletContext) extends AnyVal {

  /** Wraps an [[HttpRoutes]] and mounts it as an [[AsyncHttp4sServlet]]
    *
    * Assumes non-blocking servlet IO is available, and thus requires at least Servlet 3.1.
    */
  def mountService[F[_]: ConcurrentEffect: ContextShift](
      name: String,
      service: HttpRoutes[F],
      mapping: String = "/*"): ServletRegistration.Dynamic = {
    val servlet = new AsyncHttp4sServlet(
      service = service,
      asyncTimeout = defaults.AsyncTimeout,
      servletIo = NonBlockingServletIo(DefaultChunkSize),
      serviceErrorHandler = DefaultServiceErrorHandler[F]
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }
}

object servletContext extends ServletContextSyntax
