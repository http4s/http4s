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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.BlazeBackendBuilder
import org.http4s.blazecore.ExecutionContextConfig
import org.http4s.blazecore.tickWheelResource
import org.http4s.client.Client
import org.http4s.client.RequestKey
import org.http4s.client.defaults
import org.http4s.headers.`User-Agent`
import org.http4s.internal.BackendBuilder
import org.http4s.internal.SSLContextOption
import org.log4s.getLogger

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** Configure and obtain a BlazeClient
  * @param responseHeaderTimeout duration between the submission of a request and the completion of the response header. Does not include time to read the response body.
  * @param idleTimeout duration that a connection can wait without traffic being read or written before timeout
  * @param requestTimeout maximum duration from the submission of a request through reading the body before a timeout.
  * @param connectTimeout Duration a connection attempt times out after
  * @param userAgent optional custom user agent header
  * @param maxTotalConnections maximum connections the client will have at any specific time
  * @param maxWaitQueueLimit maximum number requests waiting for a connection at any specific time
  * @param maxConnectionsPerRequestKey Map of RequestKey to number of max connections
  * @param sslContext Some custom `SSLContext`, or `None` if the default SSL context is to be lazily instantiated.
  * @param checkEndpointIdentification require endpoint identification for secure requests according to RFC 2818, Section 3.1. If the certificate presented does not match the hostname of the request, the request fails with a CertificateException. This setting does not affect checking the validity of the cert via the sslContext's trust managers.
  * @param maxResponseLineSize maximum length of the request line
  * @param maxHeaderLength maximum length of headers
  * @param maxChunkSize maximum size of chunked content chunks
  * @param chunkBufferMaxSize Size of the buffer that is used when Content-Length header is not specified.
  * @param parserMode lenient or strict parsing mode. The lenient mode will accept illegal chars but replaces them with � (0xFFFD)
  * @param bufferSize internal buffer size of the blaze client
  * @param executionContextConfig optional custom executionContext to run async computations.
  * @param scheduler execution scheduler
  * @param asynchronousChannelGroup custom AsynchronousChannelGroup to use other than the system default
  * @param channelOptions custom socket options
  * @param customDnsResolver customDnsResolver to use other than the system default
  * @param retries the number of times an idempotent request that fails with a `SocketException` will be retried.  This is a means to deal with connections that expired while in the pool.  Retries happen immediately.  The default is 2.  For a more sophisticated retry strategy, see the [[org.http4s.client.middleware.Retry]] middleware.
  * @param maxIdleDuration maximum time a connection can be idle and still
  * be borrowed.  Helps deal with connections that are closed while
  * idling in the pool for an extended period.
  */
final class BlazeClientBuilder[F[_]] private (
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
    executionContextConfig: ExecutionContextConfig,
    val scheduler: Resource[F, TickWheelExecutor],
    val asynchronousChannelGroup: Option[AsynchronousChannelGroup],
    val channelOptions: ChannelOptions,
    val customDnsResolver: Option[RequestKey => Either[Throwable, InetSocketAddress]],
    val retries: Int,
    val maxIdleDuration: Duration,
)(implicit protected val F: Async[F])
    extends BlazeBackendBuilder[Client[F]]
    with BackendBuilder[F, Client[F]] {
  type Self = BlazeClientBuilder[F]

  protected final val logger = getLogger(this.getClass)

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
      executionContextConfig: ExecutionContextConfig = executionContextConfig,
      scheduler: Resource[F, TickWheelExecutor] = scheduler,
      asynchronousChannelGroup: Option[AsynchronousChannelGroup] = asynchronousChannelGroup,
      channelOptions: ChannelOptions = channelOptions,
      customDnsResolver: Option[RequestKey => Either[Throwable, InetSocketAddress]] =
        customDnsResolver,
      retries: Int = retries,
      maxIdleDuration: Duration = maxIdleDuration,
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
      executionContextConfig = executionContextConfig,
      scheduler = scheduler,
      asynchronousChannelGroup = asynchronousChannelGroup,
      channelOptions = channelOptions,
      customDnsResolver = customDnsResolver,
      retries = retries,
      maxIdleDuration = maxIdleDuration,
    )

  @deprecated(
    "Do not use - always returns cats.effect.unsafe.IORuntime.global.compute." +
      "There is no direct replacement - directly use Async[F].executionContext or your custom execution context",
    "0.23.5",
  )
  def executionContext: ExecutionContext = cats.effect.unsafe.IORuntime.global.compute

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
      maxConnectionsPerRequestKey: RequestKey => Int
  ): BlazeClientBuilder[F] =
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

  /** Number of times to immediately retry idempotent requests that fail
    * with a `SocketException`.
    */
  def withRetries(retries: Int = retries): BlazeClientBuilder[F] =
    copy(retries = retries)

  /** Time a connection can be idle and still be borrowed.  Helps deal
    * with connections that are closed while idling in the pool for an
    * extended period.  `Duration.Inf` means no timeout.
    */
  def withMaxIdleDuration(maxIdleDuration: Duration = maxIdleDuration): BlazeClientBuilder[F] =
    copy(maxIdleDuration = maxIdleDuration)

  /** Use some provided `SSLContext` when making secure calls, or disable secure calls with `None` */
  @deprecated(
    message =
      "Use withDefaultSslContext, withSslContext or withoutSslContext to set the SSLContext",
    since = "0.22.0-M1",
  )
  def withSslContextOption(sslContext: Option[SSLContext]): BlazeClientBuilder[F] =
    copy(sslContext =
      sslContext.fold[SSLContextOption](SSLContextOption.NoSSL)(SSLContextOption.Provided.apply)
    )

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

  /** Configures the compute thread pool used to run async computations.
    *
    * This defaults to `cats.effect.Async[F].executionContext`. In
    * almost all cases, it is desirable to use the default.
    */
  def withExecutionContext(executionContext: ExecutionContext): BlazeClientBuilder[F] =
    copy(executionContextConfig = ExecutionContextConfig.ExplicitContext(executionContext))

  def withScheduler(scheduler: TickWheelExecutor): BlazeClientBuilder[F] =
    copy(scheduler = scheduler.pure[Resource[F, *]])

  def withAsynchronousChannelGroupOption(
      asynchronousChannelGroup: Option[AsynchronousChannelGroup]
  ): BlazeClientBuilder[F] =
    copy(asynchronousChannelGroup = asynchronousChannelGroup)
  def withAsynchronousChannelGroup(
      asynchronousChannelGroup: AsynchronousChannelGroup
  ): BlazeClientBuilder[F] =
    withAsynchronousChannelGroupOption(Some(asynchronousChannelGroup))
  def withoutAsynchronousChannelGroup: BlazeClientBuilder[F] =
    withAsynchronousChannelGroupOption(None)

  def withChannelOptions(channelOptions: ChannelOptions): BlazeClientBuilder[F] =
    copy(channelOptions = channelOptions)

  def withCustomDnsResolver(
      customDnsResolver: RequestKey => Either[Throwable, InetSocketAddress]
  ): BlazeClientBuilder[F] =
    copy(customDnsResolver = Some(customDnsResolver))

  def resource: Resource[F, Client[F]] =
    resourceWithState.map(_._1)

  /** Creates a blaze-client resource along with a [[BlazeClientState]]
    * for monitoring purposes
    */
  def resourceWithState: Resource[F, (Client[F], BlazeClientState[F])] =
    for {
      dispatcher <- Dispatcher[F]
      scheduler <- scheduler
      _ <- Resource.eval(verifyAllTimeoutsAccuracy(scheduler))
      _ <- Resource.eval(verifyTimeoutRelations())
      manager <- connectionManager(scheduler, dispatcher)
      executionContext <- Resource.eval(executionContextConfig.getExecutionContext)
      client = BlazeClient.makeClient(
        manager = manager,
        responseHeaderTimeout = responseHeaderTimeout,
        requestTimeout = requestTimeout,
        scheduler = scheduler,
        ec = executionContext,
        retries = retries,
        dispatcher = dispatcher,
      )

    } yield (client, manager.state)

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
      timeoutName: String,
  ): F[Unit] =
    F.delay {
      val warningThreshold = 0.1 // 10%
      val inaccuracy = tick / timeout
      if (inaccuracy > warningThreshold)
        logger.warn(
          s"With current configuration, $timeoutName ($timeout) may be up to ${inaccuracy * 100}% longer than configured. " +
            s"If timeout accuracy is important, consider using a scheduler with a shorter tick (currently $tick)."
        )
    }

  private def verifyTimeoutRelations(): F[Unit] =
    F.delay {
      val advice =
        s"It is recommended to configure responseHeaderTimeout < requestTimeout < idleTimeout " +
          s"or disable some of them explicitly by setting them to Duration.Inf."

      if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= requestTimeout)
        logger.warn(
          s"responseHeaderTimeout ($responseHeaderTimeout) is >= requestTimeout ($requestTimeout). $advice"
        )

      if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= idleTimeout)
        logger.warn(
          s"responseHeaderTimeout ($responseHeaderTimeout) is >= idleTimeout ($idleTimeout). $advice"
        )

      if (requestTimeout.isFinite && requestTimeout >= idleTimeout)
        logger.warn(s"requestTimeout ($requestTimeout) is >= idleTimeout ($idleTimeout). $advice")
    }

  private def connectionManager(scheduler: TickWheelExecutor, dispatcher: Dispatcher[F])(implicit
      F: Async[F]
  ): Resource[F, ConnectionManager.Stateful[F, BlazeConnection[F]]] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] =
      (requestKey: RequestKey) =>
        new Http1Support[F](
          sslContextOption = sslContext,
          bufferSize = bufferSize,
          asynchronousChannelGroup = asynchronousChannelGroup,
          executionContextConfig = executionContextConfig,
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
          idleTimeout = idleTimeout,
          getAddress = customDnsResolver.getOrElse(BlazeClientBuilder.getAddress(_)),
        ).makeClient(requestKey)

    Resource.make(
      executionContextConfig.getExecutionContext.flatMap(executionContext =>
        ConnectionManager.pool(
          builder = http1,
          maxTotal = maxTotalConnections,
          maxWaitQueueLimit = maxWaitQueueLimit,
          maxConnectionsPerRequestKey = maxConnectionsPerRequestKey,
          responseHeaderTimeout = responseHeaderTimeout,
          requestTimeout = requestTimeout,
          executionContext = executionContext,
          maxIdleDuration = maxIdleDuration,
        )
      )
    )(_.shutdown)
  }
}

object BlazeClientBuilder {

  def apply[F[_]: Async]: BlazeClientBuilder[F] =
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
      executionContextConfig = ExecutionContextConfig.DefaultContext,
      scheduler = tickWheelResource,
      asynchronousChannelGroup = None,
      channelOptions = ChannelOptions(Vector.empty),
      customDnsResolver = None,
      retries = 2,
      maxIdleDuration = Duration.Inf,
    )

  @deprecated(
    "Most users should use the default execution context provided. " +
      "If you have a specific reason to use a custom one, use `.withExecutionContext`",
    "0.23.5",
  )
  def apply[F[_]: Async](executionContext: ExecutionContext): BlazeClientBuilder[F] =
    BlazeClientBuilder[F].withExecutionContext(executionContext)

  def getAddress(requestKey: RequestKey): Either[Throwable, InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
        val host = auth.host.value
        Either.catchNonFatal(new InetSocketAddress(host, port))
    }
}
