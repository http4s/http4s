package org.http4s
package syntax

import cats.data.Kleisli

trait ServiceSyntax {
  implicit def http4sServiceSyntax[A](service: Service[A, MaybeResponse]): ServiceOps[A] =
    new ServiceOps[A](service)
}


final class ServiceOps[A](self: Service[A, MaybeResponse]) {
  def orNotFound: Service[A, Response] =
    Kleisli(a => self.run(a).map(_.orNotFound))
}
