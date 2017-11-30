package org.http4s
package client
package blaze

import cats.effect._

import scala.concurrent.duration.Duration

/** Create a HTTP1 client which will attempt to recycle connections */
object PooledHttp1Client {
  private val DefaultMaxTotalConnections = 10
  private val DefaultMaxWaitQueueLimit = 256
  private val DefaultWaitExpiryTime = Duration.Inf

  /** Construct a new PooledHttp1Client
    *
    * @param maxTotalConnections maximum connections the client will have at any specific time
    * @param maxWaitQueueLimit maximum number requests waiting for a connection at any specific time
    * @param maxConnectionsPerRequestKey Map of RequestKey to number of max connections
    * @param waitExpiryTime timeout duration for request waiting in queue, default value -1, never expire
    * @param config blaze client configuration options
    */
  def apply[F[_]: Effect](
      maxTotalConnections: Int = DefaultMaxTotalConnections,
      maxWaitQueueLimit: Int = DefaultMaxWaitQueueLimit,
      maxConnectionsPerRequestKey: RequestKey => Int = _ => DefaultMaxTotalConnections,
      waitExpiryTime: RequestKey => Duration = _ => DefaultWaitExpiryTime,
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client[F] = {

    val http1: ConnectionBuilder[F, BlazeConnection[F]] = Http1Support(config)
    val pool = ConnectionManager.pool(
      http1,
      maxTotalConnections,
      maxWaitQueueLimit,
      maxConnectionsPerRequestKey,
      waitExpiryTime,
      config.executionContext)
    BlazeClient(pool, config, pool.shutdown())
  }
}
