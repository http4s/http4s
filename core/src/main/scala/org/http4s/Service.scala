package org.http4s

import cats.data._
import fs2._
import org.http4s.batteries._

object Service {
  /**
    * Lifts an unwrapped function that returns a Task into a [[Service]].
    *
    * @see [[HttpService.apply]]
    */
  def lift[A, B](f: A => Task[B]): Service[A, B] =
    Kleisli(f)

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

  /**
    * Allows Service chainig through an implicit [[Fallthrough]] instance.
    *
    */
  def withFallback[A, B : Fallthrough](fallback: Service[A, B])(service: Service[A, B]): Service[A, B] =
    service.flatMap(resp => Fallthrough[B].fallthrough(resp, fallback))
}
