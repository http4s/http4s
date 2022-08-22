/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware.internal

import cats.data.Kleisli
import cats.effect.kernel.MonadCancelThrow
import cats.~>
import org.http4s.Response
import org.http4s.server.middleware.Logger.Lift
import org.typelevel.ci.CIString

private[http4s] abstract class Logger[F[_], Self <: Logger[F, Self]] {
  self: Self =>
  def apply[G[_], A](fk: F ~> G)(
      http: Kleisli[G, A, Response[F]]
  )(implicit G: MonadCancelThrow[G]): Kleisli[G, A, Response[F]]

  def apply[G[_], A](
      http: Kleisli[G, A, Response[F]]
  )(implicit lift: Lift[F, G], G: MonadCancelThrow[G]): Kleisli[G, A, Response[F]] =
    apply(lift.fk)(http)

  def withRedactHeadersWhen(f: CIString => Boolean): Self

  def withLogAction(f: String => F[Unit]): Self
  def withLogActionOpt(of: Option[String => F[Unit]]): Self =
    of.fold(this)(withLogAction)
}
