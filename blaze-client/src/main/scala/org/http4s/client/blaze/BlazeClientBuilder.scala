package org.http4s
package client
package blaze

import cats.implicits._
import cats.effect._
import java.nio.channels.AsynchronousChannelGroup

import javax.net.ssl.SSLContext
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{BlazeBackendBuilder, tickWheelResource}
import org.http4s.headers.`User-Agent`
import org.http4s.ProductId
import org.http4s.internal.BackendBuilder
import org.log4s.getLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * @param sslContext Some custom `SSLContext`, or `None` if the
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
    val sslContext: Option[SSLContext],
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
    val channelOptions: ChannelOptions
)(implicit protected val F: ConcurrentEffect[F])
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
      sslContext: Option[SSLContext] = sslContext,
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
      channelOptions: ChannelOptions = channelOptions
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
      channelOptions = channelOptions
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
    withSslContextOption(Some(sslContext))

  /** Use an `SSLContext` obtained by `SSLContext.getDefault()` when making secure calls.
    *
    * Since 0.21, the creation is not deferred.
    */
  def withDefaultSslContext: BlazeClientBuilder[F] =
    withSslContext(SSLContext.getDefault())

  /** Use some provided `SSLContext` when making secure calls, or disable secure calls with `None` */
  def withSslContextOption(sslContext: Option[SSLContext]): BlazeClientBuilder[F] =
    copy(sslContext = sslContext)

  /** Disable secure calls */
  def withoutSslContext: BlazeClientBuilder[F] =
    copy(sslContext = None)

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
    copy(scheduler = scheduler.pure[Resource[F, ?]])

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

  def resource: Resource[F, Client[F]] =
    for {
      scheduler <- scheduler
      _ <- Resource.liftF(verifyAllTimeoutsAccuracy(scheduler))
      _ <- Resource.liftF(verifyTimeoutRelations())
      manager <- connectionManager(scheduler)
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
      timeoutName: String): F[Unit] = F.delay {
    val warningThreshold = 0.1 // 10%
    val inaccuracy = tick / timeout
    if (inaccuracy > warningThreshold) {
      logger.warn(
        s"With current configuration, $timeoutName ($timeout) may be up to ${inaccuracy * 100}% longer than configured. " +
          s"If timeout accuracy is important, consider using a scheduler with a shorter tick (currently $tick).")
    }
  }

  private def verifyTimeoutRelations(): F[Unit] = F.delay {
    val advice = s"It is recommended to configure responseHeaderTimeout < requestTimeout < idleTimeout " +
      s"or disable some of them explicitly by setting them to Duration.Inf."

    if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= requestTimeout) {
      logger.warn(
        s"responseHeaderTimeout ($responseHeaderTimeout) is >= requestTimeout ($requestTimeout). $advice")
    }

    if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= idleTimeout) {
      logger.warn(
        s"responseHeaderTimeout ($responseHeaderTimeout) is >= idleTimeout ($idleTimeout). $advice")
    }

    if (requestTimeout.isFinite && requestTimeout >= idleTimeout) {
      logger.warn(s"requestTimeout ($requestTimeout) is >= idleTimeout ($idleTimeout). $advice")
    }
  }

  private def connectionManager(scheduler: TickWheelExecutor)(
      implicit F: ConcurrentEffect[F]): Resource[F, ConnectionManager[F, BlazeConnection[F]]] = {
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
      connectTimeout = connectTimeout
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
    * @param executionContext the ExecutionContext for blaze's internal Futures
    * @param sslContext Some `SSLContext.getDefault()`, or `None` on systems where the default is unavailable
    */
  def apply[F[_]: ConcurrentEffect](
      executionContext: ExecutionContext,
      sslContext: Option[SSLContext] = tryDefaultSslContext): BlazeClientBuilder[F] =
    new BlazeClientBuilder[F](
      responseHeaderTimeout = Duration.Inf,
      idleTimeout = 1.minute,
      requestTimeout = defaults.RequestTimeout,
      connectTimeout = defaults.ConnectTimeout,
      userAgent = Some(`User-Agent`(ProductId("http4s-blaze", Some(BuildInfo.version)))),
      maxTotalConnections = 10,
      maxWaitQueueLimit = 256,
      maxConnectionsPerRequestKey = Function.const(256),
      sslContext = sslContext,
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
      channelOptions = ChannelOptions(Vector.empty)
    ) {}

  private def tryDefaultSslContext: Option[SSLContext] =
    try Some(SSLContext.getDefault())
    catch {
      case NonFatal(_) => None
    }
}
