package org.http4s
package client

import cats.effect.Effect

package object blaze {

  @deprecated("Use org.http4s.client.blaze.Http1Client instead", "0.18.0-M7")
  def defaultClient[F[_]: Effect]: Client[F] = PooledHttp1Client()

}
