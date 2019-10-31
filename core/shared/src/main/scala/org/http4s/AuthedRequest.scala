package org.http4s

import cats._
import cats.data._
import cats.implicits._

final case class AuthedRequest[F[_], A](authInfo: A, req: Request[F])

object AuthedRequest {
  def apply[F[_]: Functor, T](
      getUser: Request[F] => F[T]): Kleisli[F, Request[F], AuthedRequest[F, T]] =
    Kleisli(request => getUser(request).map(user => AuthedRequest(user, request)))
}
