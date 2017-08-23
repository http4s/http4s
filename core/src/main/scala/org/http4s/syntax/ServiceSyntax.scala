package org.http4s
package syntax

import scalaz.concurrent.Task
import scalaz.Kleisli

trait ServiceSyntax {
  implicit def http4sServiceSyntax[A](service: Service[A, MaybeResponse]): ServiceOps[A] =
    new ServiceOps[A](service)
}

final class ServiceOps[A](self: Service[A, MaybeResponse]) {
  def orNotFound(a: A): Task[Response] =
    orNotFound.run(a)

  def orNotFound: Service[A, Response] =
    Kleisli(a => self.run(a).map(_.orNotFound))
}
