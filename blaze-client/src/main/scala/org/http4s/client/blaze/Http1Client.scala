package org.http4s
package client
package blaze

import cats.effect._
import fs2.Stream

/** Create a HTTP1 client which will attempt to recycle connections */
object Http1Client {

  /** Construct a new PooledHttp1Client
    *
    * @param config blaze client configuration options
    */
  def apply[F[_]: Effect](
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): F[Client[F]] =
    Effect[F].delay(mkClient(config))

  def stream[F[_]: Effect](
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Stream[F, Client[F]] =
    Stream.bracket(apply(config))(Stream.emit(_), _.shutdown)

  private[blaze] def mkClient[F[_]: Effect](config: BlazeClientConfig): Client[F] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = Http1Support(config)
    val pool = ConnectionManager.pool(
      http1,
      config.maxTotalConnections,
      config.maxWaitQueueLimit,
      config.maxConnectionsPerRequestKey,
      config.executionContext)
    BlazeClient(pool, config, pool.shutdown())
  }

}
