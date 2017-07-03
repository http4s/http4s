package org.http4s
package servlet
package syntax

import javax.servlet.{ServletContext, ServletRegistration}

import cats.effect._
import org.http4s.server.AsyncTimeoutSupport
import org.http4s.util.threads.DefaultExecutionContext

trait ServletContextSyntax {
  implicit def ToServletContextOps(self: ServletContext): ServletContextOps = new ServletContextOps(self)
}

final class ServletContextOps private[syntax](val self: ServletContext) extends AnyVal {
  /** Wraps an HttpService and mounts it as a servlet */
  def mountService[F[_]: Effect](name: String, service: HttpService[F], mapping: String = "/*"): ServletRegistration.Dynamic = {
    val servlet = new Http4sServlet(
      service = service,
      asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
      executionContext = DefaultExecutionContext,
      servletIo = servletIo
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
