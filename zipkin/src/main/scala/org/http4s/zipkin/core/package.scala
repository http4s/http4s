package org.http4s.zipkin

import org.http4s.Request

package object core {
  def nameFromRequest(request: Request): String =
    s"${request.method} ${request.uri.path}"

}
