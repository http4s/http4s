/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.testing

import cats.effect.std.Dispatcher
import cats.{Eq, Monad}

trait EqF {
  implicit def eqF[A, F[_]: Monad](implicit eqA: Eq[A], dispatcher: Dispatcher[F]): Eq[F[A]] =
    (x: F[A], y: F[A]) =>
      dispatcher.unsafeRunSync(
        Monad[F].flatMap(x)(xResult => Monad[F].map(y)(yResult => eqA.eqv(xResult, yResult))))
}
