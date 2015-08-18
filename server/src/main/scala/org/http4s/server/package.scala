package org.http4s

import scalaz.concurrent.Task

package object server {
  /**
   * An HTTP middleware converts an [[HttpService]] to another.
   */
  type HttpMiddleware = HttpService => HttpService

  object Middleware {
    def apply(f: (Request, HttpService) => Task[Response]): HttpMiddleware = {
      service => HttpService.lift { req => f(req, service) }
    }
  }
}
