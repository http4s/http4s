package org.http4s

import cats._
import cats.data.{Kleisli, OptionT}
import cats.implicits._

/** Functions for creating [[HttpRoutes]] kleislis. */
object HttpRoutes {

  /** Lifts a function into an [[HttpRoutes]].  The application of `run`
    * is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[HttpRoutes]]
    * @param run the function to lift
    * @return an [[HttpRoutes]] that wraps `run`
    */
  def apply[F[_]: Defer](run: Request[F] => OptionT[F, Response[F]]): HttpRoutes[F] =
    Http(run)

  /** Lifts an effectful [[Response]] into an [[HttpRoutes]].
    *
    * @tparam F the effect of the [[HttpRoutes]]
    * @param fr the effectful [[Response]] to lift
    * @return an [[HttpRoutes]] that always returns `fr`
    */
  def liftF[F[_]](fr: OptionT[F, Response[F]]): HttpRoutes[F] =
    Kleisli.liftF(fr)

  /** Lifts a [[Response]] into an [[HttpRoutes]].
    *
    * @tparam F the base effect of the [[HttpRoutes]]
    * @param r the [[Response]] to lift
    * @return an [[HttpRoutes]] that always returns `r` in effect `OptionT[F, *]`
    */
  def pure[F[_]](r: Response[F])(implicit FO: Applicative[OptionT[F, *]]): HttpRoutes[F] =
    Kleisli.pure(r)

  /** Transforms an [[HttpRoutes]] on its input.  The application of the
    * transformed function is suspended in `F` to permit more
    * efficient combination of routes via `SemigroupK`.
    *
    * @tparam F the base effect of the [[HttpRoutes]]
    * @param f a function to apply to the [[Request]]
    * @param fa the [[HttpRoutes]] to transform
    * @return An [[HttpRoutes]] whose input is transformed by `f` before
    * being applied to `fa`
    */
  def local[F[_]: Defer](f: Request[F] => Request[F])(fa: HttpRoutes[F]): HttpRoutes[F] =
    Http.local[OptionT[F, *], F](f)(fa)

  /** Lifts a partial function into an [[HttpRoutes]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of routes via `SemigroupK`.
    *
    * @tparam F the base effect of the [[HttpRoutes]] - Defer suspends evaluation
    * of routes, so only 1 section of routes is checked at a time.
    * @param pf the partial function to lift
    * @return An [[HttpRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def of[F[_]: Defer: Applicative](pf: PartialFunction[Request[F], F[Response[F]]]): HttpRoutes[F] =
    Kleisli(req => OptionT(Defer[F].defer(pf.lift(req).sequence)))

  /** Lifts a partial function into an [[HttpRoutes]].  The application of the
    * partial function is not suspended in `F`, unlike [[of]]. This allows for less
    * constraints when not combining many routes.
    *
    * @tparam F the base effect of the [[HttpRoutes]]
    * @param pf the partial function to lift
    * @return An [[HttpRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def strict[F[_]: Applicative](pf: PartialFunction[Request[F], F[Response[F]]]): HttpRoutes[F] =
    Kleisli(req => OptionT(pf.lift(req).sequence))

  /** An empty set of routes.  Always responds with `OptionT.none`.
    *
    * @tparam F the base effect of the [[HttpRoutes]]
    */
  def empty[F[_]: Applicative]: HttpRoutes[F] = liftF(OptionT.none)
}
