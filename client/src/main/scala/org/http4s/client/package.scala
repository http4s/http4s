package org.http4s

import scala.concurrent.duration.DurationLong

package object client extends ClientTypes {

  object defaults {
    val ConnectTimeout = 10.seconds
    val RequestTimeout = 45.seconds
  }

}

trait ClientTypes {
  import org.http4s.client._

  type ConnectionBuilder[F[_], A <: Connection[F]] = RequestKey => F[A]

  type Middleware[F[_]] = Client[F] => Client[F]
}
