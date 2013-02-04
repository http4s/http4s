package org.http4s
package servlet

import play.api.libs.iteratee.{Enumerator, Done}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

/**
 * @author ross
 */
object Example extends App {
  val http4sServlet = new Http4sServlet({
    case req if req.pathInfo == "/ping" =>
      Done(Responder(body = Enumerator.apply(Chunk("pong".toString.getBytes))))
  })

  val rawServlet = new HttpServlet {
    override def service(req: HttpServletRequest, resp: HttpServletResponse) {
      if (req.getPathInfo == "/ping")
        resp.getWriter.write("pong")
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
