package org.http4s
package client
package blaze

/** Create a HTTP1 client which will attempt to recycle connections */
object PooledHttp1Client {

  /** Construct a new PooledHttp1Client
    *
    * @param maxTotalConnections maximum connections the client will have at any specific time
    * @param config blaze client configuration options
    */
  def apply(maxTotalConnections: Int = 10,
                         config: BlazeClientConfig = BlazeClientConfig.defaultConfig) = {
    val http1: ConnectionBuilder[BlazeConnection] = Http1Support(config)
    val pool = ConnectionManager.pool(http1, maxTotalConnections, config.executionContext)
    BlazeClient(pool, config, pool.shutdown())
  }
}
