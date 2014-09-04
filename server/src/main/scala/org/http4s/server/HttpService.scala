package org.http4s
package server

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.syntax.traverse._
import scalaz.std.option._

object HttpService {
  def apply(pf: PartialFunction[Request, Task[Response]]): HttpService = pf.lift.andThen {
    optionTask => OptionT(optionTask.sequence)
  }

  val empty: HttpService = _ => OptionT.none[Task, Response]
}
