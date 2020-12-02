/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.Semigroup
import cats.data.Kleisli
import cats.syntax.all._

package object syntax {
  @deprecated(
    "Import `cats.implicits._` and use `kleisli1 <+> kleisli2` instead. Ensure -Ypartial-unification is enabled.",
    "0.16")
  final implicit class ServiceOps[F[_], A, B](val kleisli: Kleisli[F, A, B])(implicit
      B: Semigroup[F[B]]) {
    def ||(fallback: Kleisli[F, A, B]) = orElse(fallback)
    def orElse(fallback: Kleisli[F, A, B]) = kleisli |+| fallback
  }
}
