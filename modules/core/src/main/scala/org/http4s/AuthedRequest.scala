/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.Functor
import cats.data.Kleisli

object AuthedRequest {
  def apply[F[_]: Functor, T](
      getUser: Request[F] => F[T]): Kleisli[F, Request[F], AuthedRequest[F, T]] =
    ContextRequest[F, T](getUser)

  def apply[F[_], T](context: T, req: Request[F]): AuthedRequest[F, T] =
    ContextRequest[F, T](context, req)
}
