package org.http4s

import cats.Applicative
import cats.data.Kleisli

/** Functions for creating [[Http]] kleislis. */
object Http {
  /** Lifts a function into an [[Http]] kleisli. 
    * 
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param run the function to lift
    * @return an [[Http]] that wraps `run`
    */
  def apply[F[_], G[_]](run: Request[G] => F[Response[G]]): Http[F, G] =
    Kleisli(run)

  /** Lifts an effectful [[Response]] into an [[Http]] kleisli. 
    * 
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param fr the effectful [[Response]] to lift
    * @return an [[Http]] that always returns `fr`
    */
  def liftF[F[_], G[_]](fr: F[Response[G]]): Http[F, G] =
    Kleisli.liftF(fr)

  /** Lifts a [[Response]] into an [[Http]] kleisli. 
    * 
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param r the [[Response]] to lift
    * @return an [[Http]] that always returns `r` in effect `F`
    */
  def pure[F[_]: Applicative, G[_]](r: Response[G]): Http[F, G] =
    Kleisli.pure(r)

  /** Transforms an [[Http]] on its input.
    * 
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param f a function to apply to the [[Request]]
    * @param fa the [[Http]] to transform
    * @return An [[Http]] whose input is transformed by `f` before
    * being applied to `fa`
    */
  def local[F[_], G[_]](f: Request[G] => Request[G])(fa: Http[F, G]): Http[F, G] =
    Kleisli.local(f)(fa)
}
