package org.http4s.server

import scala.util.control.{ControlThrowable, NoStackTrace}
import scalaz.concurrent.Task

/**
 * A function that returns a Task which may fail with a Pass.
 */
class Service[A, B] private[server] (val run: A => Task[B]) {
  // TODO as this matures, it will begin to look like an amalgamation of Kleisli and NullResult
  import Service.service

  def apply(a: A): Task[B] = run(a)

  def contramap[C](f: C => A): Service[C, B] = service(f andThen run)

  def map[C](f: B => C): Service[A, C] = service(a => run(a).map(f))

  def or(a: A, b: => Task[B]): Task[B] = run(a).handleWith { case Pass => b }

  def orElse(s2: Service[A, B]): Service[A, B] = service(a => run(a).handleWith { case Pass => s2.run(a) })
}

object Service extends ServiceFunctions {
  def apply[A, B](pf: PartialFunction[A, Task[B]]): Service[A, B] = service {
    pf.lift.andThen {
      case Some(resp) => resp
      case None => Task.fail(Pass)
    }
  }
}

trait ServiceFunctions {
  def service[A, B](run: A => Task[B]): Service[A, B] = new Service(run)
}

case object Pass extends ControlThrowable with NoStackTrace

