package org.http4s
package server

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.syntax.traverse._
import scalaz.std.option._

object HttpService {
  /**
   * Creates an HTTP service from a [[PartialFunction]].
   *
   * @param pf a partial function, where the optionality of the service is defined by the domain of the
   *           partial function.
   * @return An [[OptionT]] containining a [[Task[Some[Response]]] if [[pf]] is defined at the request,
   *         or else [[NoResponse]]
   */
  def apply(pf: PartialFunction[Request, Task[Response]]): HttpService = pf.lift.andThen {
    optionTask => OptionT(optionTask.sequence)
  }

  /**
   * An HTTP service that generates the same response for any request.
   *
   * @param resp an optional, asynchronous response
   */
  def constant(resp: OptionT[Task, Response]): HttpService = _ => resp

  /**
   * An HTTP service that never creates a response.
   */
  val empty: HttpService = constant(NoResponse)
}
