package org.http4s
package syntax

import cats.Functor
import cats.data.OptionT

trait KleisliSyntax {
  implicit def http4sKleisliResponseSyntax[F[_]: Functor, A](
    service: A => OptionT[F, Response[F]]): KleisliResponseOps[F, A] =
    new KleisliResponseOps[F, A](service)
}

final class KleisliResponseOps[F[_]: Functor, A](self: A => OptionT[F, Response[F]]) {
  def orNotFound: A => F[Response[F]] =
    a => self(a).getOrElse(Response.notFound)
}
