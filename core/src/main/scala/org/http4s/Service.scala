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

import cats.{Applicative, Monoid, Semigroup}
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.all._

@deprecated("Deprecated in favor of Kleisli", "0.18")
object Service {

  /** Lifts a total function to a `Service`. The function is expected to handle
    * all requests it is given.  If `f` is a `PartialFunction`, use `apply`
    * instead.
    */
  def lift[F[_], A, B](f: A => F[B]): Service[F, A, B] =
    Kleisli(f)

  /** Lifts a partial function to an `Service`.  Responds with the
    * zero of [B] for any request where `pf` is not defined.
    */
  def apply[F[_], A, B: Monoid](pf: PartialFunction[A, F[B]])(implicit
      F: Applicative[F]): Service[F, A, B] =
    lift(req => pf.applyOrElse(req, Function.const(F.pure(Monoid[B].empty))))

  /** Lifts a F into a [[Service]].
    */
  def const[F[_], A, B](b: F[B]): Service[F, A, B] =
    lift(_ => b)

  /**  Lifts a value into a [[Service]].
    */
  def constVal[F[_], A, B](b: => B)(implicit F: Sync[F]): Service[F, A, B] =
    lift(_ => F.delay(b))

  /** Allows Service chaining through a Monoid instance. */
  def withFallback[F[_], A, B](fallback: Service[F, A, B])(service: Service[F, A, B])(implicit
      M: Semigroup[F[B]]): Service[F, A, B] =
    service |+| fallback

  /** A service that always returns the zero of B. */
  def empty[F[_]: Sync, A, B: Monoid]: Service[F, A, B] =
    constVal(Monoid[B].empty)
}
