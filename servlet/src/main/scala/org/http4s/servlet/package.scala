package org.http4s

import cats._
import cats.implicits._

package object servlet {
  protected[servlet] type BodyWriter[F[_]] = Response[F] => F[Unit]

  protected[servlet] def NullBodyWriter[F[_]: Applicative]: BodyWriter[F] = { _ =>
    ().pure[F]
  }

  protected[servlet] val DefaultChunkSize = 4096
}
