package org.http4s

import cats.Applicative
import cats.data.Kleisli

/** Functions for creating [[HttpApp]]s. */
object HttpApp {
  /** Lifts a function into an [[HttpApp]]. 
    * 
    * @tparam F the effect of the [[HttpApp]].
    * @param run the function to lift
    * @return an [[HttpApp]] that wraps `run`
    */
  def apply[F[_]](run: Request[F] => F[Response[F]]): HttpApp[F] =
    Kleisli(run)

  /** Lifts an effectful [[Response]] into an [[HttpApp]]. 
    * 
    * @tparam F the effect of the [[HttpApp]]
    * @param fr the effectful [[Response]] to lift
    * @return an [[HttpApp]] that always returns `fr`
    */
  def liftF[F[_]](fr: F[Response[F]]): HttpApp[F] =
    Kleisli.liftF(fr)

  /** Lifts a [[Response]] into an [[HttpApp]]. 
    * 
    * @tparam F the effect of the [[HttpApp]]
    * @param r the [[Response]] to lift
    * @return an [[Http]] that always returns `r` in effect `F`
    */
  def pure[F[_]: Applicative](r: Response[F]): HttpApp[F] =
    Kleisli.pure(r)

  /** Transforms an [[HttpApp]] on its input.
    * 
    * @tparam F the effect of the [[HttpApp]]
    * @param f a function to apply to the [[Request]]
    * @param fa the [[HttpApp]] to transform
    * @return An [[HttpApp]] whose input is transformed by `f` before
    * being applied to `fa`
    */
  def local[F[_]](f: Request[F] => Request[F])(fa: HttpApp[F]): HttpApp[F] =
    Kleisli.local[F, Response[F], Request[F]](f)(fa)
}
