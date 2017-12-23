package org.http4s.client.blaze

import cats.effect.Effect
import org.http4s.client.{Client, RequestKey}

object PooledHttp1Client {

  /** Construct a new PooledHttp1Client
    *
    * @param maxTotalConnections         maximum connections the client will have at any specific time
    * @param maxWaitQueueLimit           maximum number requests waiting for a connection at any specific time
    * @param maxConnectionsPerRequestKey Map of RequestKey to number of max connections
    * @param config                      blaze client configuration options
    */
  @deprecated("Use org.http4s.client.blaze.Http1Client instead", "0.18.0-M7")
  def apply[F[_]: Effect](
      maxTotalConnections: Int = bits.DefaultMaxTotalConnections,
      maxWaitQueueLimit: Int = bits.DefaultMaxWaitQueueLimit,
      maxConnectionsPerRequestKey: RequestKey => Int = _ => bits.DefaultMaxTotalConnections,
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client[F] =
    Http1Client.mkClient(
      config.copy(
        maxTotalConnections = maxTotalConnections,
        maxWaitQueueLimit = maxWaitQueueLimit,
        maxConnectionsPerRequestKey = maxConnectionsPerRequestKey
      ))
}
