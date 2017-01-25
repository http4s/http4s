package org.http4s
package server

import cats.Monoid
import fs2.Task

package object syntax {

  @deprecated("Import `scalaz.syntax.semigroup._` and use `service1 |+| service2` instead", "0.16")
  final implicit class ServiceOps[A, B](val service: Service[A, B])(implicit B: Monoid[Task[B]]) {
    def ||    (fallback: Service[A, B]) = orElse(fallback)
    def orElse(fallback: Service[A, B]) = Service.withFallback(fallback)(service)
  }

}
