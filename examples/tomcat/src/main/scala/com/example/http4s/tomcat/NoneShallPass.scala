package com.example.http4s.tomcat

import javax.servlet._
import javax.servlet.http._

import org.http4s.servlet.DefaultFilter

object NoneShallPass extends DefaultFilter {
  override def doHttpFilter(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain): Unit =
    response.setStatus(403)
}
