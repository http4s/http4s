package org.http4s
package client

import cats.effect._

package object blaze {

  /** Default blaze client
    *
    * This client will create a new connection for every request.
    */
  def defaultClient[F[_]: Effect]: Client[F] = SimpleHttp1Client(BlazeClientConfig.defaultConfig)
}
