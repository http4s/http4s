package org.http4s
package servlet

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import scala.concurrent.Future
import scalaz.concurrent.Task
import scalaz.effect.IO
import scalaz.Free.Trampoline

/**
 * @author ross
 */
object ServletExample extends App {

  import concurrent.ExecutionContext.Implicits.global

  val taskServlet = new Http4sServlet(new ExampleRoute().apply())

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
      else if (req.getPathInfo == "/bigstring2") {
        for (i <- 0 to 1000) {
          resp.getOutputStream.write(s"This is string number $i".getBytes())
          resp.flushBuffer()
        }
      }
    }
  }

  val server = new Server(8080)
  val context = new ServletContextHandler()
  context.setContextPath("/")
  server.setHandler(context);
  context.addServlet(new ServletHolder(taskServlet), "/http4s/*")
  context.addServlet(new ServletHolder(rawServlet), "/raw/*")
  server.start()
  server.join()
}
