package org.http4s

package object servlet {
  protected[servlet] type BodyWriter[F[_]] = (Response[F], F[Unit]) => F[Unit]

  protected[servlet] def NullBodyWriter[F[_]]: BodyWriter[F] =
    (_, timeout) => timeout

  protected[servlet] val DefaultChunkSize = 4096
}
