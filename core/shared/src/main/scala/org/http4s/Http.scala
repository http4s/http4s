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


/** A function with a [[Request]] input and a [[Response]] output.  This type
    * is useful for writing middleware that are polymorphic over the return
    * type F.
    *
    * @tparam F the effect type in which the [[Response]] is returned
    * @tparam G the effect type of the [[Request]] and [[Response]] bodies
    */
trait Http[F[_], G[_]] { self =>

  def apply(req: Request[G]): F[Response[G]]

  def <+>(other: Http[F, G])(implicit F: Alternative[F]): Http[F, G] =
    req => self(req) <+> other(req)

  def map(post: Response[G] => Response[G])(implicit F: Functor[F]): Http[F, G] =
    req => self(req).map(post)

  def contramap(pre: Request[G] => Request[G])(implicit F: Monad[F]): Http[F, G] =
    req => F.unit >> self(pre(req))

  def local(pre: Request[G] => Request[G])(implicit F: Monad[F]): Http[F, G] =
    contramap(pre)
}

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
  def apply[F[_], G[_]](run: Request[G] => F[Response[G]])(implicit F: Monad[F]): Http[F, G] =
    req => F.unit >> run(req)

  /** Lifts an effectful [[Response]] into an [[Http]] kleisli.
    *
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param fr the effectful [[Response]] to lift
    * @return an [[Http]] that always returns `fr`
    */
  def liftF[F[_], G[_]](fr: F[Response[G]]): Http[F, G] =
    _ => fr

  /** Lifts a [[Response]] into an [[Http]] kleisli.
    *
    * @tparam F the effect of the [[Response]] returned by the [[Http]]
    * @tparam G the effect of the bodies of the [[Request]] and [[Response]]
    * @param r the [[Response]] to lift
    * @return an [[Http]] that always returns `r` in effect `F`
    */
  def pure[F[_], G[_]](r: Response[G])(implicit F: Applicative[F]): Http[F, G] =
    _ => F.pure(r)

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
  def local[F[_], G[_]](f: Request[G] => Request[G])(fa: Http[F, G])(implicit
      F: Monad[F]
  ): Http[F, G] =
    (req => F.unit >> fa(f(req)))
}
