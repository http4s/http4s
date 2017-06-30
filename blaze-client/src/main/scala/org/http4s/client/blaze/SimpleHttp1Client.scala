package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {
  /** create a new simple client
    *
    * @param config blaze configuration object
    */
  def apply[F[_]: Effect](config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client[F] = {
    val (executionContext, shutdown) = bits.getExecutionContext(config)
    val manager: ConnectionManager[F, BlazeConnection[F]] =
      ConnectionManager.basic(Http1Support(config, executionContext))

    BlazeClient(manager, config, manager.shutdown() >> shutdown)
  }
}
