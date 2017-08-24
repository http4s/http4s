package org.http4s
package syntax

import cats._
import cats.data.Kleisli
import cats.implicits._

trait ServiceSyntax {
  implicit def http4sServiceSyntax[F[_]: Functor, A](service: Service[F, A, MaybeResponse[F]]): ServiceOps[F, A] =
    new ServiceOps[F, A](service)
}

final class ServiceOps[F[_]: Functor, A](self: Service[F, A, MaybeResponse[F]]) {
  def orNotFound: Service[F, A, Response[F]] =
    Kleisli(a => self.run(a).map(_.orNotFound))
}
