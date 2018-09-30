/* https://github.com/http4s/http4s/issues/454#issuecomment-160144299 */
package org.http4s
package server
package jetty

import cats.effect.{ContextShift, IO}
import org.eclipse.jetty.server.{HttpConfiguration, HttpConnectionFactory, Server, ServerConnector}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.http4s.dsl.io._
import org.http4s.servlet.AsyncHttp4sServlet

object Issue454 {
  implicit val cs: ContextShift[IO] = Http4sSpec.TestContextShift

  // If the bug is not triggered right away, try increasing or
  // repeating the request. Also if you decrease the data size (to
  // say 32mb, the bug does not manifest so often, but the stack
  // trace is a bit different.
  val insanelyHugeData = Array.ofDim[Byte](1024 * 1024 * 128)

  {
    var i = 0
    while (i < insanelyHugeData.length) {
      insanelyHugeData(i) = ('0' + i).toByte
      i = i + 1
    }
    insanelyHugeData(insanelyHugeData.length - 1) = '-' // end marker
  }

  def main(args: Array[String]): Unit = {
    val server = new Server

    val connector = new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()))
    connector.setPort(5555)

    val context = new ServletContextHandler
    context.setContextPath("/")
    context.addServlet(new ServletHolder(servlet), "/")

    server.addConnector(connector)
    server.setHandler(context)

    server.start()
  }

  val servlet = new AsyncHttp4sServlet[IO](
    service = HttpRoutes.of[IO] {
      case GET -> Root => Ok(insanelyHugeData)
    },
    servletIo = org.http4s.servlet.NonBlockingServletIo(4096),
    serviceErrorHandler = DefaultServiceErrorHandler
  )
}
