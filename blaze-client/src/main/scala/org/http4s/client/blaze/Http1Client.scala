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
  def apply[F[_]](config: BlazeClientConfig = BlazeClientConfig.defaultConfig)(
      implicit F: Effect[F]): F[Client[F]] =
    F.delay(mkClient(config))

  def stream[F[_]: Effect](
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Stream[F, Client[F]] =
    Stream.bracket(apply(config))(_.shutdown)

  private[blaze] def mkClient[F[_]: Effect](config: BlazeClientConfig): Client[F] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = Http1Support(config)
    val pool = ConnectionManager.pool(
      builder = http1,
      maxTotal = config.maxTotalConnections,
      maxWaitQueueLimit = config.maxWaitQueueLimit,
      maxConnectionsPerRequestKey = config.maxConnectionsPerRequestKey,
      responseHeaderTimeout = config.responseHeaderTimeout,
      requestTimeout = config.requestTimeout,
      executionContext = config.executionContext
    )
    BlazeClient(pool, config, pool.shutdown())
  }

}
