package org.http4s.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

/**
  * Mainly a convenience for our servlet examples, but, hey, why not.
  */
trait DefaultFilter extends Filter {
  override def init(filterConfig: FilterConfig): Unit = {}

  override def destroy(): Unit = {}

  final override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit =
    (request, response) match {
      case (httpReq: HttpServletRequest, httpRes: HttpServletResponse) =>
        doHttpFilter(httpReq, httpRes, chain)
      case _ =>
    }

  def doHttpFilter(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain): Unit
}
