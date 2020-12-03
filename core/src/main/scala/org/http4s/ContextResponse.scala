/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats._

final case class ContextResponse[F[_], A](context: A, response: Response[F]) {
  def mapContext[B](f: A => B): ContextResponse[F, B] =
    ContextResponse(f(context), response)

  def mapK[G[_]](fk: F ~> G): ContextResponse[G, A] =
    ContextResponse(context, response.mapK(fk))
}

object ContextResponse{}
