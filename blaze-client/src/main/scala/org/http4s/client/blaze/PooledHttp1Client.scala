package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._

/** Create a HTTP1 client which will attempt to recycle connections */
object PooledHttp1Client {
  private val DefaultMaxTotalConnections = 10

  /** Construct a new PooledHttp1Client
    *
    * @param maxTotalConnections maximum connections the client will have at any specific time
    * @param config blaze client configuration options
    */
  def apply[F[_]: Effect](maxTotalConnections: Int = DefaultMaxTotalConnections,
                          config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client[F] = {

    val (executionContext, shutdown) = bits.getExecutionContext[F](config)
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = Http1Support(config, executionContext)
    val pool = ConnectionManager.pool(http1, maxTotalConnections, executionContext)
    BlazeClient(pool, config, pool.shutdown() >> shutdown)
  }
}
