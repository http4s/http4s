package org.http4s

import scalaz.concurrent.Task

package object server {
  type Middleware  = HttpService => HttpService
  type HttpService = Service[Request, Response]

  object HttpService {
    def apply(pf: PartialFunction[Request, Task[Response]]): HttpService = Service(pf)
  }
}


