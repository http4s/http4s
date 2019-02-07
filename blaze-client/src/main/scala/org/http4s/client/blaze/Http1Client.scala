package org.http4s
package client
package blaze

import cats.effect._
import fs2.Stream
import org.http4s.blaze.channel.ChannelOptions

/** Create a HTTP1 client which will attempt to recycle connections */
@deprecated("Use BlazeClientBuilder", "0.19.0-M2")
object Http1Client {

  /** Construct a new PooledHttp1Client
    *
    * @param config blaze client configuration options
    */
  private def resource[F[_]](config: BlazeClientConfig)(
      implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = new Http1Support(
      sslContextOption = config.sslContext,
      bufferSize = config.bufferSize,
      asynchronousChannelGroup = config.group,
      executionContext = config.executionContext,
      checkEndpointIdentification = config.checkEndpointIdentification,
      maxResponseLineSize = config.maxResponseLineSize,
      maxHeaderLength = config.maxHeaderLength,
      maxChunkSize = config.maxChunkSize,
      chunkBufferMaxSize = config.chunkBufferMaxSize,
      parserMode = if (config.lenientParser) ParserMode.Lenient else ParserMode.Strict,
      userAgent = config.userAgent,
      channelOptions = ChannelOptions(Vector.empty)
    ).makeClient

    Resource
      .make(
        ConnectionManager
          .pool(
            builder = http1,
            maxTotal = config.maxTotalConnections,
            maxWaitQueueLimit = config.maxWaitQueueLimit,
            maxConnectionsPerRequestKey = config.maxConnectionsPerRequestKey,
            responseHeaderTimeout = config.responseHeaderTimeout,
            requestTimeout = config.requestTimeout,
            executionContext = config.executionContext
          ))(_.shutdown)
      .map(pool => BlazeClient(pool, config, pool.shutdown, config.executionContext))
  }

  def stream[F[_]](config: BlazeClientConfig = BlazeClientConfig.defaultConfig)(
      implicit F: ConcurrentEffect[F]): Stream[F, Client[F]] =
    Stream.resource(resource(config))
}
