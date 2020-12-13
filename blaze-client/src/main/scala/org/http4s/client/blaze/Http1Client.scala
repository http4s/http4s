/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client
package blaze

import cats.effect._
import fs2.Stream
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.internal.SSLContextOption

import scala.concurrent.duration.Duration

/** Create a HTTP1 client which will attempt to recycle connections */
@deprecated("Use BlazeClientBuilder", "0.19.0-M2")
object Http1Client {

  /** Construct a new PooledHttp1Client
    *
    * @param config blaze client configuration options
    */
  private def resource[F[_]](config: BlazeClientConfig)(implicit
      F: ConcurrentEffect[F]): Resource[F, Client[F]] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = new Http1Support(
      sslContextOption =
        config.sslContext.fold[SSLContextOption](SSLContextOption.NoSSL)(SSLContextOption.Provided),
      bufferSize = config.bufferSize,
      asynchronousChannelGroup = config.group,
      executionContext = config.executionContext,
      scheduler = bits.ClientTickWheel,
      checkEndpointIdentification = config.checkEndpointIdentification,
      maxResponseLineSize = config.maxResponseLineSize,
      maxHeaderLength = config.maxHeaderLength,
      maxChunkSize = config.maxChunkSize,
      chunkBufferMaxSize = config.chunkBufferMaxSize,
      parserMode = if (config.lenientParser) ParserMode.Lenient else ParserMode.Strict,
      userAgent = config.userAgent,
      channelOptions = ChannelOptions(Vector.empty),
      connectTimeout = Duration.Inf
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

  def stream[F[_]](config: BlazeClientConfig = BlazeClientConfig.defaultConfig)(implicit
      F: ConcurrentEffect[F]): Stream[F, Client[F]] =
    Stream.resource(resource(config))
}
