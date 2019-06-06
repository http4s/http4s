package org.http4s

import cats.{~>, Functor}
import cats.data.Kleisli
import cats.implicits._

final case class AuthedRequest[F[_], A](authInfo: A, req: Request[F]) {
  def mapK[G[_]](fk: F ~> G): AuthedRequest[G, A] =
    AuthedRequest(authInfo, req.mapK(fk))
}

object AuthedRequest {
  def apply[F[_]: Functor, T](
      getUser: Request[F] => F[T]): Kleisli[F, Request[F], AuthedRequest[F, T]] =
    Kleisli(request => getUser(request).map(user => AuthedRequest(user, request)))
}
