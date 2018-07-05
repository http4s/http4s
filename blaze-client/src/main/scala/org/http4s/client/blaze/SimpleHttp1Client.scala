package org.http4s
package client
package blaze

import cats.effect._

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {

  /** create a new simple client
    *
    * @param config blaze configuration object
    */
  @deprecated("Use Http1Client instead", "0.18.0-M7")
  def apply[F[_]: Concurrent](
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client[F] = {
    val manager: ConnectionManager[F, BlazeConnection[F]] =
      ConnectionManager.basic(Http1Support(config))
    BlazeClient(manager, config, manager.shutdown())
  }
}
