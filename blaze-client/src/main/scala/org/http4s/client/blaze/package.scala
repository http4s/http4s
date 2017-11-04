package org.http4s
package client

import cats.effect._
import fs2.Stream

package object blaze {

  def defaultClient[F[_]: Effect]: F[Client[F]] = Http1Client()

  def defaultClientStream[F[_]: Effect]: Stream[F, Client[F]] = Http1Client.stream()

  @deprecated("Use Http1Client instead", "0.18.0-M6")
  type PooledHttp1Client = Http1Client.type
}
