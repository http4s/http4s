package org.http4s
package server
package middleware

import fs2._
import cats._
import cats.implicits._

/** Handles HEAD requests as a GET without a body.
  * 
  * If the service returns the fallthrough response, the request is resubmitted
  * as a GET.  The resulting response's body is killed, but all headers are
  * preserved.  This is a naive, but correct, implementation of HEAD.  Routes
  * requiring more optimization should implement their own HEAD handler.
  */
object DefaultHead {
  def apply(service: HttpService)(implicit M: Monoid[Task[MaybeResponse]]): HttpService =
    HttpService.lift { req =>
      req.method match {
        case Method.HEAD =>
          (service |+| headAsTruncatedGet(service))(req)
        case _ =>
          service(req)
    }
  }

  private def headAsTruncatedGet(service: HttpService) =
    HttpService.lift { req =>
      val getReq = req.withMethod(Method.GET)
      // TODO fs2 port I think .open.close is a fair translation of
      // scalaz-stream's kill, but it doesn't run the cleanup.  Is
      // this a bug?
      service(getReq).map(_.cata(resp => resp.copy(body = resp.body.drain), Pass))
    }
}
