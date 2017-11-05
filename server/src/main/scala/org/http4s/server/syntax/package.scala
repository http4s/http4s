package org.http4s
package server

import cats.Semigroup
import cats.data.Kleisli
import cats.implicits._

package object syntax {
  @deprecated("Import `cats.implicits._` and `org.http4s.implicits._` and use `service1 <+> service2` instead. Ensure -Ypartial-unification is enabled.", "0.16")
  final implicit class ServiceOps[F[_], A, B](val service: Kleisli[F, A, B])(
      implicit B: Semigroup[F[B]]) {
    def ||(fallback: Kleisli[F, A, B]) = orElse(fallback)
    def orElse(fallback: Kleisli[F, A, B]) = service |+| fallback
  }
}
