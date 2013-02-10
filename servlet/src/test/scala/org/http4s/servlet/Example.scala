package org.http4s
package servlet

import play.api.libs.iteratee.{Concurrent, Enumerator, Done}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

import Bodies._
import concurrent.Future

/**
 * @author ross
 */
object Example extends App {

  import concurrent.ExecutionContext.Implicits.global

  val http4sServlet = new Http4sServlet({
    case req if req.pathInfo == "/ping" =>
      Future.successful(Responder(body = Enumerator("pong".getBytes())))

    case req if req.pathInfo == "/stream" =>
      Future.successful(Responder(body = Concurrent.unicast({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/echo" =>
      Future.successful(Responder(body = req.body))
  })

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
