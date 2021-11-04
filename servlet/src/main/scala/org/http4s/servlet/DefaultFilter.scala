/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** Mainly a convenience for our servlet examples, but, hey, why not.
  */
trait DefaultFilter extends Filter {
  override def init(filterConfig: FilterConfig): Unit = {}

  override def destroy(): Unit = {}

  override final def doFilter(
      request: ServletRequest,
      response: ServletResponse,
      chain: FilterChain,
  ): Unit =
    (request, response) match {
      case (httpReq: HttpServletRequest, httpRes: HttpServletResponse) =>
        doHttpFilter(httpReq, httpRes, chain)
      case _ =>
    }

  def doHttpFilter(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain,
  ): Unit
}
