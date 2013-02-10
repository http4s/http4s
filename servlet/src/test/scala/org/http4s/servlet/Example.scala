package org.http4s
package servlet

import play.api.libs.iteratee._
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

import Bodies._
import concurrent.Future
import org.http4s.Responder
import org.http4s.Request

/**
 * @author ross
 */
object Example extends App {
  val http4sServlet = new Http4sServlet(ExampleRoute())

  val rawServlet = new HttpServlet {
    override def service(req: HttpServletRequest, resp: HttpServletResponse) {
      if (req.getPathInfo == "/ping")
        resp.getWriter.write("pong")
      else if (req.getPathInfo == "/echo") {
        val bytes = new Array[Byte](8 * 1024);
        var in = 0
        while ({in = req.getInputStream.read(bytes); in >= 0}) {
          resp.getOutputStream.write(bytes, 0, in)
          resp.flushBuffer()
        }
      }
    }
  }

  val server = new Server(8080)
  val context = new ServletContextHandler()
  context.setContextPath("/")
  server.setHandler(context);
  context.addServlet(new ServletHolder(http4sServlet), "/http4s/*")
  context.addServlet(new ServletHolder(rawServlet), "/raw/*")
  server.start()
  server.join()
}
