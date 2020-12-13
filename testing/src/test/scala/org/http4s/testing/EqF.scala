/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.testing

import cats.Eq
import cats.effect.std.Dispatcher

trait EqF {
  implicit def eqF[A, F[_]](implicit eqA: Eq[A], dispatcher: Dispatcher[F]): Eq[F[A]] =
    Eq.by[F[A], A](f => dispatcher.unsafeRunSync(f))
}
