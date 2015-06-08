package org.http4s
package server
package middleware

import scalaz.concurrent.Task

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {

  def apply(service: HttpService): HttpService = {
    Service.lift { req =>
      service.run(req).flatMap {
        case r@Some(_) => Task.now(r)
        case None      =>
          val p = req.uri.path
          if (p.isEmpty || p.charAt(p.length - 1) != '/') Task.now(None)
          else {
            val withSlash = req.copy(uri = req.uri.copy(path = p.substring(0, p.length - 1)))
            service.run(withSlash)
          }
      }
    }
  }

}
