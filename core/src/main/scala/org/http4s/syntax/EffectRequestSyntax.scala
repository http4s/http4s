package org.http4s
package syntax

import cats._
import cats.implicits._

trait EffectRequestSyntax extends Any {
  @deprecated("Use map or flatMap directly on the request", "0.18.0-M2")
  implicit def http4sEffectRequestSyntax[F[_]](req: F[Request[F]]): EffectRequestOps[F] =
    new EffectRequestOps[F](req)
}

final class EffectRequestOps[F[_]](val self: F[Request[F]])
    extends AnyVal
    with EffectMessageSyntax[F, Request[F]]
    with RequestOps[F] {

  def decodeWith[A](decoder: EntityDecoder[F, A], strict: Boolean)(f: A => F[Response[F]])(
      implicit F: Monad[F]): F[Response[F]] =
    self.flatMap(_.decodeWith(decoder, strict)(f))

  def withPathInfo(pi: String)(implicit F: Functor[F]): F[Request[F]] =
    self.map(_.withPathInfo(pi))

}
