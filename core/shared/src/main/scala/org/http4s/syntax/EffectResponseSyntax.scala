package org.http4s
package syntax

import cats._
import cats.implicits._

trait EffectResponseSyntax extends Any {
  @deprecated("Use map or flatMap directly on the response", "0.18.0-M2")
  implicit def http4sEffectResponseSyntax[F[_]](resp: F[Response[F]]): EffectResponseOps[F] =
    new EffectResponseOps[F](resp)
}

final class EffectResponseOps[F[_]](val self: F[Response[F]])
    extends AnyVal
    with EffectMessageSyntax[F, Response[F]]
    with ResponseOps[F] {

  override def withStatus(status: Status)(implicit F: Functor[F]): Self =
    self.map(_.withStatus(status))

}
