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
package blaze
package client

import cats.syntax.all._
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Dispatcher
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{BlazeBackendBuilder, tickWheelResource}
import org.http4s.client.{Client, ConnectionBuilder, RequestKey, defaults}
import org.http4s.headers.`User-Agent`
import org.http4s.internal.{BackendBuilder, SSLContextOption}
import org.log4s.getLogger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** @param sslContext Some custom `SSLContext`, or `None` if the
  * default SSL context is to be lazily instantiated.
  */
sealed abstract class BlazeClientBuilder[F[_]] private (
    val responseHeaderTimeout: Duration,
    val idleTimeout: Duration,
    val requestTimeout: Duration,
    val connectTimeout: Duration,
    val userAgent: Option[`User-Agent`],
    val maxTotalConnections: Int,
    val maxWaitQueueLimit: Int,
    val maxConnectionsPerRequestKey: RequestKey => Int,
    val sslContext: SSLContextOption,
    val checkEndpointIdentification: Boolean,
    val maxResponseLineSize: Int,
    val maxHeaderLength: Int,
    val maxChunkSize: Int,
    val chunkBufferMaxSize: Int,
    val parserMode: ParserMode,
    val bufferSize: Int,
    val executionContext: ExecutionContext,
    val scheduler: Resource[F, TickWheelExecutor],
    val asynchronousChannelGroup: Option[AsynchronousChannelGroup],
    val channelOptions: ChannelOptions,
    val customDnsResolver: Option[RequestKey => Either[Throwable, InetSocketAddress]]
)(implicit protected val F: Async[F])
    extends BlazeBackendBuilder[Client[F]]
    with BackendBuilder[F, Client[F]] {
  type Self = BlazeClientBuilder[F]

  final protected val logger = getLogger(this.getClass)

  private def copy(
      responseHeaderTimeout: Duration = responseHeaderTimeout,
      idleTimeout: Duration = idleTimeout,
      requestTimeout: Duration = requestTimeout,
      connectTimeout: Duration = connectTimeout,
      userAgent: Option[`User-Agent`] = userAgent,
      maxTotalConnections: Int = maxTotalConnections,
      maxWaitQueueLimit: Int = maxWaitQueueLimit,
      maxConnectionsPerRequestKey: RequestKey => Int = maxConnectionsPerRequestKey,
      sslContext: SSLContextOption = sslContext,
      checkEndpointIdentification: Boolean = checkEndpointIdentification,
      maxResponseLineSize: Int = maxResponseLineSize,
      maxHeaderLength: Int = maxHeaderLength,
      maxChunkSize: Int = maxChunkSize,
      chunkBufferMaxSize: Int = chunkBufferMaxSize,
      parserMode: ParserMode = parserMode,
      bufferSize: Int = bufferSize,
      executionContext: ExecutionContext = executionContext,
      scheduler: Resource[F, TickWheelExecutor] = scheduler,
      asynchronousChannelGroup: Option[AsynchronousChannelGroup] = asynchronousChannelGroup,
      channelOptions: ChannelOptions = channelOptions,
      customDnsResolver: Option[RequestKey => Either[Throwable, InetSocketAddress]] = None
  ): BlazeClientBuilder[F] =
    new BlazeClientBuilder[F](
      responseHeaderTimeout = responseHeaderTimeout,
      idleTimeout = idleTimeout,
      requestTimeout = requestTimeout,
      connectTimeout = connectTimeout,
      userAgent = userAgent,
      maxTotalConnections = maxTotalConnections,
      maxWaitQueueLimit = maxWaitQueueLimit,
      maxConnectionsPerRequestKey = maxConnectionsPerRequestKey,
      sslContext = sslContext,
      checkEndpointIdentification = checkEndpointIdentification,
      maxResponseLineSize = maxResponseLineSize,
      maxHeaderLength = maxHeaderLength,
      maxChunkSize = maxChunkSize,
      chunkBufferMaxSize = chunkBufferMaxSize,
      parserMode = parserMode,
      bufferSize = bufferSize,
      executionContext = executionContext,
      scheduler = scheduler,
      asynchronousChannelGroup = asynchronousChannelGroup,
      channelOptions = channelOptions,
      customDnsResolver = customDnsResolver
    ) {}

  def withResponseHeaderTimeout(responseHeaderTimeout: Duration): BlazeClientBuilder[F] =
    copy(responseHeaderTimeout = responseHeaderTimeout)

  def withMaxHeaderLength(maxHeaderLength: Int): BlazeClientBuilder[F] =
    copy(maxHeaderLength = maxHeaderLength)

  def withIdleTimeout(idleTimeout: Duration): BlazeClientBuilder[F] =
    copy(idleTimeout = idleTimeout)

  def withRequestTimeout(requestTimeout: Duration): BlazeClientBuilder[F] =
    copy(requestTimeout = requestTimeout)

  def withConnectTimeout(connectTimeout: Duration): BlazeClientBuilder[F] =
    copy(connectTimeout = connectTimeout)

  def withUserAgentOption(userAgent: Option[`User-Agent`]): BlazeClientBuilder[F] =
    copy(userAgent = userAgent)
  def withUserAgent(userAgent: `User-Agent`): BlazeClientBuilder[F] =
    withUserAgentOption(Some(userAgent))
  def withoutUserAgent: BlazeClientBuilder[F] =
    withUserAgentOption(None)

  def withMaxTotalConnections(maxTotalConnections: Int): BlazeClientBuilder[F] =
    copy(maxTotalConnections = maxTotalConnections)

  def withMaxWaitQueueLimit(maxWaitQueueLimit: Int): BlazeClientBuilder[F] =
    copy(maxWaitQueueLimit = maxWaitQueueLimit)

  def withMaxConnectionsPerRequestKey(
      maxConnectionsPerRequestKey: RequestKey => Int): BlazeClientBuilder[F] =
    copy(maxConnectionsPerRequestKey = maxConnectionsPerRequestKey)

  /** Use the provided `SSLContext` when making secure calls */
  def withSslContext(sslContext: SSLContext): BlazeClientBuilder[F] =
    copy(sslContext = SSLContextOption.Provided(sslContext))

  /** Use an `SSLContext` obtained by `SSLContext.getDefault()` when making secure calls.
    *
    * Since 0.21, the creation is not deferred.
    */
  def withDefaultSslContext: BlazeClientBuilder[F] =
    withSslContext(SSLContext.getDefault())

  /** Use some provided `SSLContext` when making secure calls, or disable secure calls with `None` */
  @deprecated(
    message =
      "Use withDefaultSslContext, withSslContext or withoutSslContext to set the SSLContext",
    since = "0.22.0-M1")
  def withSslContextOption(sslContext: Option[SSLContext]): BlazeClientBuilder[F] =
    copy(sslContext =
      sslContext.fold[SSLContextOption](SSLContextOption.NoSSL)(SSLContextOption.Provided.apply))

  /** Disable secure calls */
  def withoutSslContext: BlazeClientBuilder[F] =
    copy(sslContext = SSLContextOption.NoSSL)

  def withCheckEndpointAuthentication(checkEndpointIdentification: Boolean): BlazeClientBuilder[F] =
    copy(checkEndpointIdentification = checkEndpointIdentification)

  def withMaxResponseLineSize(maxResponseLineSize: Int): BlazeClientBuilder[F] =
    copy(maxResponseLineSize = maxResponseLineSize)

  def withMaxChunkSize(maxChunkSize: Int): BlazeClientBuilder[F] =
    copy(maxChunkSize = maxChunkSize)

  def withChunkBufferMaxSize(chunkBufferMaxSize: Int): BlazeClientBuilder[F] =
    copy(chunkBufferMaxSize = chunkBufferMaxSize)

  def withParserMode(parserMode: ParserMode): BlazeClientBuilder[F] =
    copy(parserMode = parserMode)

  def withBufferSize(bufferSize: Int): BlazeClientBuilder[F] =
    copy(bufferSize = bufferSize)

  def withExecutionContext(executionContext: ExecutionContext): BlazeClientBuilder[F] =
    copy(executionContext = executionContext)

  def withScheduler(scheduler: TickWheelExecutor): BlazeClientBuilder[F] =
    copy(scheduler = scheduler.pure[Resource[F, *]])

  def withAsynchronousChannelGroupOption(
      asynchronousChannelGroup: Option[AsynchronousChannelGroup]): BlazeClientBuilder[F] =
    copy(asynchronousChannelGroup = asynchronousChannelGroup)
  def withAsynchronousChannelGroup(
      asynchronousChannelGroup: AsynchronousChannelGroup): BlazeClientBuilder[F] =
    withAsynchronousChannelGroupOption(Some(asynchronousChannelGroup))
  def withoutAsynchronousChannelGroup: BlazeClientBuilder[F] =
    withAsynchronousChannelGroupOption(None)

  def withChannelOptions(channelOptions: ChannelOptions): BlazeClientBuilder[F] =
    copy(channelOptions = channelOptions)

  def withCustomDnsResolver(customDnsResolver: RequestKey => Either[Throwable, InetSocketAddress])
      : BlazeClientBuilder[F] =
    copy(customDnsResolver = Some(customDnsResolver))

  def resource: Resource[F, Client[F]] =
    for {
      dispatcher <- Dispatcher[F]
      scheduler <- scheduler
      _ <- Resource.eval(verifyAllTimeoutsAccuracy(scheduler))
      _ <- Resource.eval(verifyTimeoutRelations())
      manager <- connectionManager(scheduler, dispatcher)
    } yield BlazeClient.makeClient(
      manager = manager,
      responseHeaderTimeout = responseHeaderTimeout,
      idleTimeout = idleTimeout,
      requestTimeout = requestTimeout,
      scheduler = scheduler,
      ec = executionContext
    )

  private def verifyAllTimeoutsAccuracy(scheduler: TickWheelExecutor): F[Unit] =
    for {
      _ <- verifyTimeoutAccuracy(scheduler.tick, responseHeaderTimeout, "responseHeaderTimeout")
      _ <- verifyTimeoutAccuracy(scheduler.tick, idleTimeout, "idleTimeout")
      _ <- verifyTimeoutAccuracy(scheduler.tick, requestTimeout, "requestTimeout")
      _ <- verifyTimeoutAccuracy(scheduler.tick, connectTimeout, "connectTimeout")
    } yield ()

  private def verifyTimeoutAccuracy(
      tick: Duration,
      timeout: Duration,
      timeoutName: String): F[Unit] =
    F.delay {
      val warningThreshold = 0.1 // 10%
      val inaccuracy = tick / timeout
      if (inaccuracy > warningThreshold)
        logger.warn(
          s"With current configuration, $timeoutName ($timeout) may be up to ${inaccuracy * 100}% longer than configured. " +
            s"If timeout accuracy is important, consider using a scheduler with a shorter tick (currently $tick).")
    }

  private def verifyTimeoutRelations(): F[Unit] =
    F.delay {
      val advice =
        s"It is recommended to configure responseHeaderTimeout < requestTimeout < idleTimeout " +
          s"or disable some of them explicitly by setting them to Duration.Inf."

      if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= requestTimeout)
        logger.warn(
          s"responseHeaderTimeout ($responseHeaderTimeout) is >= requestTimeout ($requestTimeout). $advice")

      if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= idleTimeout)
        logger.warn(
          s"responseHeaderTimeout ($responseHeaderTimeout) is >= idleTimeout ($idleTimeout). $advice")

      if (requestTimeout.isFinite && requestTimeout >= idleTimeout)
        logger.warn(s"requestTimeout ($requestTimeout) is >= idleTimeout ($idleTimeout). $advice")
    }

  private def connectionManager(scheduler: TickWheelExecutor, dispatcher: Dispatcher[F])(implicit
      F: Async[F]): Resource[F, ConnectionManager[F, BlazeConnection[F]]] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = new Http1Support(
      sslContextOption = sslContext,
      bufferSize = bufferSize,
      asynchronousChannelGroup = asynchronousChannelGroup,
      executionContext = executionContext,
      scheduler = scheduler,
      checkEndpointIdentification = checkEndpointIdentification,
      maxResponseLineSize = maxResponseLineSize,
      maxHeaderLength = maxHeaderLength,
      maxChunkSize = maxChunkSize,
      chunkBufferMaxSize = chunkBufferMaxSize,
      parserMode = parserMode,
      userAgent = userAgent,
      channelOptions = channelOptions,
      connectTimeout = connectTimeout,
      dispatcher = dispatcher,
      getAddress = customDnsResolver.getOrElse(BlazeClientBuilder.getAddress(_))
    ).makeClient
    Resource.make(
      ConnectionManager.pool(
        builder = http1,
        maxTotal = maxTotalConnections,
        maxWaitQueueLimit = maxWaitQueueLimit,
        maxConnectionsPerRequestKey = maxConnectionsPerRequestKey,
        responseHeaderTimeout = responseHeaderTimeout,
        requestTimeout = requestTimeout,
        executionContext = executionContext
      ))(_.shutdown)
  }
}

object BlazeClientBuilder {

  /** Creates a BlazeClientBuilder
    *
    * @param executionContext the ExecutionContext for blaze's internal Futures. Most clients should pass scala.concurrent.ExecutionContext.global
    */
  def apply[F[_]: Async](executionContext: ExecutionContext): BlazeClientBuilder[F] =
    new BlazeClientBuilder[F](
      responseHeaderTimeout = Duration.Inf,
      idleTimeout = 1.minute,
      requestTimeout = defaults.RequestTimeout,
      connectTimeout = defaults.ConnectTimeout,
      userAgent = Some(`User-Agent`(ProductId("http4s-blaze", Some(BuildInfo.version)))),
      maxTotalConnections = 10,
      maxWaitQueueLimit = 256,
      maxConnectionsPerRequestKey = Function.const(256),
      sslContext = SSLContextOption.TryDefaultSSLContext,
      checkEndpointIdentification = true,
      maxResponseLineSize = 4096,
      maxHeaderLength = 40960,
      maxChunkSize = Int.MaxValue,
      chunkBufferMaxSize = 1024 * 1024,
      parserMode = ParserMode.Strict,
      bufferSize = 8192,
      executionContext = executionContext,
      scheduler = tickWheelResource,
      asynchronousChannelGroup = None,
      channelOptions = ChannelOptions(Vector.empty),
      customDnsResolver = None
    ) {}

  def getAddress(requestKey: RequestKey): Either[Throwable, InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
        val host = auth.host.value
        Either.catchNonFatal(new InetSocketAddress(host, port))
    }
}
