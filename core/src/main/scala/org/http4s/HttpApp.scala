package org.http4s

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Sync

/** Functions for creating [[HttpApp]] kleislis. */
object HttpApp {

  /** Lifts a function into an [[HttpApp]].  The application of `run` is
    * suspended in `F` to permit more efficient combination of routes
    * via `SemigroupK`.
    *
    * @tparam F the effect of the [[HttpApp]].
    * @param run the function to lift
    * @return an [[HttpApp]] that wraps `run`
    */
  def apply[F[_]: Sync](run: Request[F] => F[Response[F]]): HttpApp[F] =
    Http(run)

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

  /** Transforms an [[HttpApp]] on its input.  The application of the
    * transformed function is suspended in `F` to permit more
    * efficient combination of routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[HttpApp]]
    * @param f a function to apply to the [[Request]]
    * @param fa the [[HttpApp]] to transform
    * @return An [[HttpApp]] whose input is transformed by `f` before
    * being applied to `fa`
    */
  def local[F[_]](f: Request[F] => Request[F])(fa: HttpApp[F])(implicit F: Sync[F]): HttpApp[F] =
    Http.local(f)(fa)

  /** An app that always returns `404 Not Found`. */
  def notFound[F[_]: Applicative]: HttpApp[F] = pure(Response.notFound)
}
