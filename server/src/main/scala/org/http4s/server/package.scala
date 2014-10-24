package org.http4s

import scalaz.concurrent.Task

package object server {
  type HttpService = Service[Request, Response]

  object HttpService {
    def apply(pf: PartialFunction[Request, Task[Response]]): HttpService = Service(pf)
  }
}


