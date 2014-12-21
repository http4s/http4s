package org.http4s.servlet.syntax

import javax.servlet.{ServletRegistration, ServletContext}

import org.http4s.server.{AsyncTimeoutSupport, HttpService}
import org.http4s.servlet.Http4sServlet

import scalaz.concurrent.Strategy
import scalaz.syntax.Ops

/**
 * Created by ross on 12/19/14.
 */
trait ServletContextSyntax {
  implicit def ToServletContextOps(self: ServletContext): ServletContextOps = new ServletContextOps(self)
}

final class ServletContextOps private[syntax](val self: ServletContext) extends AnyVal {
  /** Wraps an HttpService and mounts it as a servlet */
  def mountService(name: String, service: HttpService, mapping: String = "/*"): ServletRegistration.Dynamic = {
    val servlet = new Http4sServlet(
      service = service,
      asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
      threadPool = Strategy.DefaultExecutorService,
      chunkSize = 4096
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }
}

object servletContext extends ServletContextSyntax
