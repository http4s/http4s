package org.http4s

import scala.util.control.{NoStackTrace, ControlThrowable}
import scalaz.concurrent.Task

package object server {
  /** Defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]].  A service may optionally return a Task failed with
    * [[Pass]] to delegate.  Unhandled Pass failures are treated as a 404.
    */
  type HttpService = Request => Task[Response]

  implicit class TaskFunctionSyntax[A, B](val run: A => Task[B]) {
    def map[C](f: B => C): A => Task[C] = a => run(a).map(f)

    def or[A1 <: A, B1 >: B](a: A1, b: => Task[B1]): Task[B1] =
      run(a).handleWith { case Pass => b }

    def orElse[A1 <: A, B1 >: B](that: A1 => Task[B1]): A1 => Task[B1] = { a: A1 =>
      run(a).handleWith { case Pass => that.run(a) }
    }
  }

  implicit class HttpServiceSyntax(val run: Request => Task[Response]) {
    def orNotFound(req: Request): Task[Response] =
      run.or(req, ResponseBuilder.notFound(req))
  }
}

case object Pass extends ControlThrowable with NoStackTrace

