package org.http4s
package server

import cats.Monad
import cats.data.{Kleisli, OptionT}

object ContextMiddleware {
  def apply[F[_]: Monad, T](
      getContext: Kleisli[OptionT[F, ?], Request[F], T]): ContextMiddleware[F, T] =
    _.compose(Kleisli((r: Request[F]) => getContext(r).map(ContextRequest(_, r))))
}
