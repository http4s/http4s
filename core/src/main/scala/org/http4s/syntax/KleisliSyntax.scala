package org.http4s
package syntax

import cats.{Functor, ~>}
import cats.syntax.functor._
import cats.effect.Sync
import cats.data.{Kleisli, OptionT}

trait KleisliSyntax {
  implicit def http4sKleisliResponseSyntax[F[_]: Functor, A](
      service: Kleisli[OptionT[F, ?], A, Response[F]]): KleisliResponseOps[F, A] =
    new KleisliResponseOps[F, A](service)
}

trait KleisliSyntaxBinCompat0 {
  implicit def http4sKleisliHttpRoutesSyntax[F[_]: Sync](
      routes: HttpRoutes[F]): KleisliHttpRoutesOps[F] =
    new KleisliHttpRoutesOps[F](routes)

  implicit def http4sKleisliHttpAppSyntax[F[_]: Sync](app: HttpApp[F]): KleisliHttpAppOps[F] =
    new KleisliHttpAppOps[F](app)
}

final class KleisliResponseOps[F[_]: Functor, A](self: Kleisli[OptionT[F, ?], A, Response[F]]) {
  def orNotFound: Kleisli[F, A, Response[F]] =
    Kleisli(a => self.run(a).getOrElse(Response.notFound))
}

final class KleisliHttpRoutesOps[F[_]: Sync](self: HttpRoutes[F]) {
  def translate[G[_]: Sync](fk: F ~> G)(gK: G ~> F): HttpRoutes[G] =
    HttpRoutes(request => self.run(request.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}

final class KleisliHttpAppOps[F[_]: Sync](self: HttpApp[F]) {
  def translate[G[_]: Sync](fk: F ~> G)(gK: G ~> F): HttpApp[G] =
    HttpApp(request => fk(self.run(request.mapK(gK)).map(_.mapK(fk))))
}
