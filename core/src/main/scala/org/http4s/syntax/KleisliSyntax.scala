package org.http4s
package syntax

import cats._
import cats.data.{Kleisli, OptionT}

trait KleisliSyntax {
  implicit def http4sKleisliResponseSyntax[F[_]: Functor](
      service: Kleisli[OptionT[F, ?], Request[F], Response[F]]): KleisliResponseOps[F] =
    new KleisliResponseOps[F](service)
}

final class KleisliResponseOps[F[_]: Functor](val self: Kleisli[OptionT[F, ?], Request[F], Response[F]]) extends AnyVal{
  def orNotFound: Http[F, F] =
    Kleisli(a => self.run(a).getOrElse(Response.notFound))
}
