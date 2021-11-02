/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Applicative
import cats.Monad
import cats.data.Kleisli

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
  def apply[F[_]: Monad](run: Request[F] => F[Response[F]]): HttpApp[F] =
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
  def local[F[_]](f: Request[F] => Request[F])(fa: HttpApp[F])(implicit F: Monad[F]): HttpApp[F] =
    Http.local(f)(fa)

  /** An app that always returns `404 Not Found`. */
  def notFound[F[_]: Applicative]: HttpApp[F] = pure(Response.notFound)
}
