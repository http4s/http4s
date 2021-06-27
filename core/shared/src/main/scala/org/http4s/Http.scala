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

import cats._
import cats.syntax.all._
import cats.data.Kleisli

/** Functions for creating [[Http]] kleislis. */
object Http {

  /** Lifts a function into an [[Http]] kleisli.  The application of
    * `run` is suspended in `F` to permit more efficient combination
    * of routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param run the function to lift
    * @return an [[Http]] that suspends `run`.
    */
  def apply[F[_], Body](run: Request[Body] => F[Response[Body]])(implicit F: Monad[F]): Http[F, Body] =
    Kleisli(req => F.unit >> run(req))

  /** Lifts an effectful [[Response]] into an [[Http]] kleisli.
    *
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param fr the effectful [[Response]] to lift
    * @return an [[Http]] that always returns `fr`
    */
  def liftF[F[_], Body](fr: F[Response[Body]]): Http[F, Body] =
    Kleisli.liftF(fr)

  /** Lifts a [[Response]] into an [[Http]] kleisli.
    *
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param r the [[Response]] to lift
    * @return an [[Http]] that always returns `r` in effect `F`
    */
  def pure[F[_]: Applicative, Body](r: Response[Body]): Http[F, Body] =
    Kleisli.pure(r)

  /** Transforms an [[Http]] on its input.  The application of the
    * transformed function is suspended in `F` to permit more
    * efficient combination of routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param f a function to apply to the [[Request]]
    * @param fa the [[Http]] to transform
    * @return An [[Http]] whose input is transformed by `f` before
    * being applied to `fa`
    */
  def local[F[_], Body](f: Request[Body] => Request[Body])(fa: Http[F, Body])(implicit
      F: Monad[F]): Http[F, Body] =
    Kleisli(req => F.unit >> fa.run(f(req)))
}
