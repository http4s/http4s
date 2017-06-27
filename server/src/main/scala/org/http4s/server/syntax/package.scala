package org.http4s
package server

import cats.Monoid

package object syntax {
  @deprecated("Import `cats.syntax.semigroup._` and use `service1 |+| service2` instead", "0.16")
  final implicit class ServiceOps[F[_], A, B](val service: Service[F, A, B])(implicit B: Monoid[F[B]]) {
    def ||    (fallback: Service[F, A, B]) = orElse(fallback)
    def orElse(fallback: Service[F, A, B]) = Service.withFallback(fallback)(service)
  }
}
