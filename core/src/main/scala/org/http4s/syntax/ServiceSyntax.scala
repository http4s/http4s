package org.http4s
package syntax

import cats._
import cats.implicits._

trait ServiceSyntax {
  implicit def http4sServiceSyntax[F[_], A, B](service: Service[F, A, B]): ServiceOps[F, A, B] =
    new ServiceOps[F, A, B](service)
}

final class ServiceOps[F[_], A, B](self: Service[F, A, B]) {
  def orNotFound(a: A)(implicit F: Functor[F], ev: B <:< MaybeResponse[F]): F[Response[F]] =
    self.run(a).map(_.orNotFound)
}
