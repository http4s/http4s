package org.http4s

import cats._
import cats.arrow.Choice
import cats.data._
import fs2._
import org.http4s.batteries._

object Service {

  implicit def serviceChoice: Choice[Service] = new Choice[Service] {
    override def choice[A, B, C](
      f: Service[A, C],
      g: Service[B, C]
    ): Service[Either[A, B], C] =
      Kleisli(
               (a: Either[A, B]) =>
                 a match {
                   case Right(r) => g(r)
                   case Left(l) => f(l)
                 }
             )
    override def id[A]: Service[A, A] = Kleisli[Task, A, A](Task.now)
    override def compose[A, B, C](
      f: Service[B, C],
      g: Service[A, B]
    ): Service[A, C] =
      Kleisli((r: A) => g(r).flatMap(x => f(x)))
  }

  /**
    * Lifts a total function to a `Service`. The function is expected to handle
    * all requests it is given.  If `f` is a `PartialFunction`, use `apply`
    * instead.
    */
  def lift[A, B](f: A => Task[B]): Service[A, B] =
    Kleisli(f)

  /** Lifts a partial function to an `Service`.  Responds with the
    * zero of [B] for any request where `pf` is not defined.
    */
  def apply[A, B: Monoid](pf: PartialFunction[A, Task[B]]): Service[A, B] =
    lift(req => pf.applyOrElse(req, Function.const(Task.now(Monoid[B].empty))))

  /**
    * Lifts a Task into a [[Service]].
    *
    */
  def const[A, B](b: Task[B]): Service[A, B] =
    lift(_ => b)

  /**
    *  Lifts a value into a [[Service]].
    *
    */
  def constVal[A, B](b: => B): Service[A, B] =
    lift(_ => Task.delay(b))

  /** Allows Service chainig through a `scalaz.Monoid` instance. */
  def withFallback[A, B](fallback: Service[A, B])(service: Service[A, B])(implicit M: Monoid[Task[B]]): Service[A, B] =
    service |+| fallback

  /** A service that always returns the zero of B. */
  def empty[A, B: Monoid]: Service[A, B] =
    constVal(Monoid[B].empty)
}
