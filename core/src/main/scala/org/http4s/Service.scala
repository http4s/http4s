package org.http4s

import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.syntax.kleisli._

object Service {
  /**
    * Lifts a total function to a `Service`. The function is expected to handle
    * all requests it is given.  If `f` is a `PartialFunction`, use `apply`
    * instead.
    */
  def lift[A, B](f: A => Task[B]): Service[A, B] = Kleisli.kleisli(f)

  /** Lifts a partial function to an `Service`.  Responds with the
    * fallthrough instance [B] for any request where `pf` is not defined.
    */
  def apply[A, B: Fallthrough](pf: PartialFunction[A, Task[B]]): Service[A, B] =
    lift(req => pf.applyOrElse(req, Function.const(Task.now(Fallthrough[B].fallthrough))))

  /**
    * Lifts a Task into a [[Service]].
    *
    */
  def const[A, B](b: => Task[B]): Service[A, B] = b.liftKleisli

  /**
    *  Lifts a value into a [[Service]].
    *
    */
  def constVal[A, B](b: => B): Service[A, B] = Task.now(b).liftKleisli

  /**
    * Allows Service chainig through an implicit [[Fallthrough]] instance.
    *
    */
  def withFallback[A, B : Fallthrough](fallback: Service[A, B])(service: Service[A, B]): Service[A, B] =
    service.flatMap(resp => Fallthrough[B].fallthrough(resp, fallback))

  /** A service that always falls through */
  def empty[A, B: Fallthrough]: Service[A, B] =
    constVal(Fallthrough[B].fallthrough)
}
