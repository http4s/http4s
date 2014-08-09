package com.example.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

class RawServlet extends HttpServlet {
  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    if (req.getPathInfo == "/ping")
      resp.getWriter.write("pong")
    else if (req.getPathInfo == "/echo") {
      val bytes = new Array[Byte](8 * 1024)
      var in = 0
      while ( {
        in = req.getInputStream.read(bytes); in >= 0
      }) {
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
