package org.http4s
package server

import scalaz.concurrent.Task

object HttpService {
  def apply(pf: PartialFunction[Request, Task[Response]]): HttpService = Service.apply(pf)

  def httpService(run: Request => Task[Response]): HttpService = Service.service(run)

  val empty: HttpService = Service.service(_ => Task.fail(Pass))
}
