package org.http4s
package server

import scalaz._
import scalaz.concurrent.Task
import Service._

/**
 * A service is a function from a request to an asynchronous response.  It is a
 * Kleisli arrow, specialized on Task, with additional methods to deal with empty
 * responses.
 *
 * @param run the underlying function
 * @tparam A the request type
 * @tparam B the response type
 */
final case class Service[A, B](run: A => Task[B]) {
  /**
   * Returns an asynchronous response to a request
   */
  def apply(a: A): Task[B] =
    run(a)

  def contramap[C](f: C => A): Service[C, B] =
    Service(run compose f)

  def map[C](f: B => C): Service[A, C] =
    Service(run.andThen(_.map(f)))

  def flatMapTask[C](f: B => Task[C]): Service[A, C] =
    Service(run.andThen(_.flatMap(f)))

  def flatMap[C](f: B => Service[A, C]): Service[A, C] =
    Service(a => run(a).flatMap(f(_).run(a)))

  def kleisli: Kleisli[Task, A, B] = Kleisli.kleisli(run)
}

object Service {
  implicit def serviceInstance[A]: Catchable[({type λ[α] = Service[A, α]})#λ]
    with MonadError[({type λ[α, β] = Service[A, β]})#λ, Throwable] =
      new Catchable[({type λ[α] = Service[A, α]})#λ]
        with MonadError[({type λ[α, β] = Service[A, β]})#λ, Throwable] {

      override def fail[B](err: Throwable): Service[A, B] =
        Service(_ => Task.taskInstance.fail(err))

      override def attempt[B](f: Service[A, B]): Service[A, \/[Throwable, B]] =
        Service(f(_).attempt)

      override def raiseError[B](e: Throwable): Service[A, B] =
        Service(_ => Task.taskInstance.raiseError(e))

      override def handleError[B](fa: Service[A, B])(f: (Throwable) => Service[A, B]): Service[A, B] =
        Service { a =>
          Task.taskInstance.handleError(fa(a))(f.andThen(_.run(a)))
        }

      override def point[B](a: => B): Service[A, B] =
        Service(_ => Task.taskInstance.point(a))

      override def bind[B, C](fa: Service[A, B])(f: B => Service[A, C]): Service[A, C] = fa.flatMap(f)
    }
}
