package org.http4s
package servlet
package syntax

import javax.servlet.{ServletContext, ServletRegistration}

import org.http4s.server.AsyncTimeoutSupport

trait ServletContextSyntax {
  implicit def ToServletContextOps(self: ServletContext): ServletContextOps = new ServletContextOps(self)
}

final class ServletContextOps private[syntax](val self: ServletContext) extends AnyVal {
  /** Wraps an HttpService and mounts it as a servlet */
  def mountService(name: String, service: HttpService, mapping: String = "/*"): ServletRegistration.Dynamic = {
    val servlet = new Http4sServlet(
      service = service,
      asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
      // TODO fs2 port
      // This is garbage how do we shut this down I just want it to compile argh
      threadPool = org.http4s.util.threads.newDefaultFixedThreadPool(
        4, org.http4s.util.threads.threadFactory(i => s"org.http4s.blaze.server.DefaultExecutor-$i")
      ),
      servletIo = servletIo
    )
    val reg = self.addServlet(name, servlet)
    reg.setLoadOnStartup(1)
    reg.setAsyncSupported(true)
    reg.addMapping(mapping)
    reg
  }

  private def servletIo: ServletIo = {
    val version = ServletApiVersion(self.getMajorVersion, self.getMinorVersion)
    if (version >= ServletApiVersion(3, 1))
      NonBlockingServletIo(DefaultChunkSize)
    else
      BlockingServletIo(DefaultChunkSize)
  }
}

object servletContext extends ServletContextSyntax
