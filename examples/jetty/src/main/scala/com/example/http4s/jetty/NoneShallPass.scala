/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.http4s.jetty

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.http4s.servlet.DefaultFilter

object NoneShallPass extends DefaultFilter {
  override def doHttpFilter(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain): Unit =
    response.setStatus(403)
}
