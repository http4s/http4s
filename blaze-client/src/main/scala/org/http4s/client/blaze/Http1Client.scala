package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import fs2.Stream

/** Create a HTTP1 client which will attempt to recycle connections */
@deprecated("Use BlazeClientBuilder", "0.19.0-M2")
object Http1Client {

  /** Construct a new PooledHttp1Client
    *
    * @param config blaze client configuration options
    */
  def apply[F[_]](config: BlazeClientConfig = BlazeClientConfig.defaultConfig)(
      implicit F: ConcurrentEffect[F]): F[Client[F]] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = new Http1Support(
      sslContextOption = config.sslContext,
      bufferSize = config.bufferSize,
      asynchronousChannelGroup = config.group,
      executionContext = config.executionContext,
      checkEndpointIdentification = config.checkEndpointIdentification,
      maxResponseLineSize = config.maxResponseLineSize,
      maxHeaderLength = config.maxHeaderLength,
      maxChunkSize = config.maxChunkSize,
      parserMode = if (config.lenientParser) ParserMode.Lenient else ParserMode.Strict,
      userAgent = config.userAgent
    ).makeClient

    ConnectionManager
      .pool(
        builder = http1,
        maxTotal = config.maxTotalConnections,
        maxWaitQueueLimit = config.maxWaitQueueLimit,
        maxConnectionsPerRequestKey = config.maxConnectionsPerRequestKey,
        responseHeaderTimeout = config.responseHeaderTimeout,
        requestTimeout = config.requestTimeout,
        executionContext = config.executionContext
      )
      .map(pool => BlazeClient(pool, config, pool.shutdown()))
  }

  def stream[F[_]: ConcurrentEffect](
      config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Stream[F, Client[F]] =
    Stream.bracket(apply(config))(_.shutdown)
}
