package org.http4s
package client
package blaze

import cats.effect._

/** Create a HTTP1 client which will attempt to recycle connections */
object PooledHttp1Client {
  private val DefaultMaxTotalConnections = 10

  /** Construct a new PooledHttp1Client
    *
    * @param maxTotalConnections maximum connections the client will have at any specific time
    * @param maxConnectionsPerRequestKey Map of RequestKey to number of max connections
    * @param config blaze client configuration options
    */
  def apply[F[_]: Effect](
      maxTotalConnections: Int = DefaultMaxTotalConnections,
      maxConnectionsPerRequestKey: Map[RequestKey, Int] = Map.empty,
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client[F] = {

    val http1: ConnectionBuilder[F, BlazeConnection[F]] = Http1Support(config)
    val pool = ConnectionManager.pool(
      http1,
      maxTotalConnections,
      maxConnectionsPerRequestKey,
      config.executionContext)
    BlazeClient(pool, config, pool.shutdown())
  }
}
