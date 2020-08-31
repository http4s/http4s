/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.Monad
import cats.implicits._
import cats.data.{Kleisli, OptionT}

object ContextMiddleware {
  def apply[F[_]: Monad, T](
      getContext: Kleisli[OptionT[F, *], Request[F], T]): ContextMiddleware[F, T] =
    _.compose(Kleisli((r: Request[F]) => getContext(r).map(ContextRequest(_, r))))

  /**
    * Useful for Testing, Construct a Middleware from a single
    * value T to use as the context
    *
    * @param t The value to use as the context
    * @return A ContextMiddleware that always provides T
    */
  def const[F[_]: Monad, T](t: T): ContextMiddleware[F, T] =
    apply(Kleisli(_ => t.pure[OptionT[F, *]]))
}
