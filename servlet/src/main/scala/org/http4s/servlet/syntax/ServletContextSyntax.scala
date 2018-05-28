package org.http4s
package servlet
package syntax

import cats.effect._
import javax.servlet.{ServletContext, ServletRegistration}
import org.http4s.server.{AsyncTimeoutSupport, DefaultServiceErrorHandler}
import scala.concurrent.ExecutionContext

trait ServletContextSyntax {
  implicit def ToServletContextOps(self: ServletContext): ServletContextOps =
    new ServletContextOps(self)
}

final class ServletContextOps private[syntax] (val self: ServletContext) extends AnyVal {

  /** Wraps an [[HttpRoutes]] and mounts it as an [[AsyncHttp4sServlet]] */
  def mountService[F[_]: ConcurrentEffect](name: String, service: HttpRoutes[F], mapping: String = "/*")(
      implicit ec: ExecutionContext = ExecutionContext.global): ServletRegistration.Dynamic = {
    val servlet = new AsyncHttp4sServlet(
      service = service,
      asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
      executionContext = ec,
      servletIo = servletIo,
      serviceErrorHandler = DefaultServiceErrorHandler[F]
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }

  private def servletIo[F[_]: Effect]: ServletIo[F] = {
    val version = ServletApiVersion(self.getMajorVersion, self.getMinorVersion)
    if (version >= ServletApiVersion(3, 1))
      NonBlockingServletIo(DefaultChunkSize)
    else
      BlockingServletIo(DefaultChunkSize)
  }
}

object servletContext extends ServletContextSyntax
