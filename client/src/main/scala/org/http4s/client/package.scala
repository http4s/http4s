package org.http4s

package object client extends ClientTypes

trait ClientTypes {
  import org.http4s.client._

  type ConnectionBuilder[F[_], A <: Connection[F]] = RequestKey => F[A]

  type Middleware[F[_]] = Client[F] => Client[F]
}
