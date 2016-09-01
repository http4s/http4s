package org.http4s

import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.syntax.kleisli._

object Service {
  /**
    * Lifts an unwrapped function that returns a Task into a [[Service]].
    *
    * @see [[HttpService.apply]]
    */
  def lift[A, B](f: A => Task[B]): Service[A, B] = Kleisli.kleisli(f)

  /** Alternative application which lifts a partial function to a [[Service]],
    * answering with a [[Response]] with status [[Status.NotFound]] for any requests
    * where the function is undefined.
    */
  def apply[A, B](pf: PartialFunction[A, Task[B]], default: Service[A, B] = Service.empty): Service[A, B] =
    lift(req => pf.applyOrElse(req, default))

  /** Alternative application which lifts a partial function to an [[Service]],
    * answering with a [[Response]] as supplied by the default argument.
    */
  def apply[A, B](pf: PartialFunction[A, Task[B]], default: Task[B]): Service[A, B] =
    apply(pf, const[A, B](default))

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

  def empty[A, B: Fallthrough]: Service[A, B] =
    constVal(Fallthrough[B].fallthroughValue)
}
