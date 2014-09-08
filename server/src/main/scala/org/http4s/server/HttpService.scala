package org.http4s
package server

import scalaz.concurrent.Task

object HttpService {
  def apply(pf: PartialFunction[Request, Task[Response]]): HttpService = pf.lift.andThen {
    case Some(resp) => resp
    case None => Task.fail(Pass)
  }

  val empty: HttpService = _ => Task.fail(Pass)
}
