package org.http4s

import cats.Functor
import cats.syntax.functor._

final case class AuthedRequest[F[_], A](authInfo: A, req: Request[F])

object AuthedRequest {
  def apply[F[_]: Functor, T](getUser: Request[F] => F[T])
      : Request[F] => F[AuthedRequest[F, T]] =
    request => getUser(request).map(user => AuthedRequest(user, request))
}
