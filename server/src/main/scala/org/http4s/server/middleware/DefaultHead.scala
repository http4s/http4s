package org.http4s
package server
package middleware

import scalaz.concurrent.Task
import org.http4s.server.syntax._

/** Handles HEAD requests as a GET without a body.
  * 
  * If the service returns the fallthrough response, the request is resubmitted
  * as a GET.  The resulting response's body is killed, but all headers are
  * preserved.  This is a naive, but correct, implementation of HEAD.  Routes
  * requiring more optimization should implement their own HEAD handler.
  */
object DefaultHead {
  def apply(service: HttpService): HttpService =
    HttpService.lift { req =>
      req.method match {
        case Method.HEAD =>
          (service orElse headAsTruncatedGet(service))(req)
        case _ =>
          service(req)
    }
  }

  private def headAsTruncatedGet(service: HttpService) =
    HttpService.lift { req =>
      val getReq = req.copy(method = Method.GET)
      service(getReq).map(resp => resp.copy(body = resp.body.kill))
    }
}
