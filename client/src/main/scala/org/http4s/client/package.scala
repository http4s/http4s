package org.http4s

import cats.data.Kleisli
import fs2.Stream

package object client extends ClientTypes

trait ClientTypes {
  import org.http4s.client._

  type Client[F[_]] = Kleisli[Stream[F, ?], Request[F], Response[F]]

  type ConnectionBuilder[F[_], A <: Connection[F]] = RequestKey => F[A]

  type Middleware[F[_]] = Client[F] => Client[F]
}
