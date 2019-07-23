package org.http4s
package client
package blaze

import cats.effect._
import java.nio.channels.AsynchronousChannelGroup

import javax.net.ssl.SSLContext
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{BlazeBackendBuilder, tickWheelResource}
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.internal.BackendBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

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
    val scheduler: Option[TickWheelExecutor],
    val asynchronousChannelGroup: Option[AsynchronousChannelGroup],
    val channelOptions: ChannelOptions
)(implicit protected val F: ConcurrentEffect[F])
    extends BlazeBackendBuilder[Client[F]]
    with BackendBuilder[F, Client[F]] {
  type Self = BlazeClientBuilder[F]

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
      scheduler: Option[TickWheelExecutor] = scheduler,
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
    copy(sslContext = Some(sslContext))

  /** Use an `SSLContext` obtained by `SSLContext.getDefault()` when making secure calls.
    *
    * The creation of the context is lazy, as `SSLContext.getDefault()` throws on some
    * platforms.  The context creation is deferred until the first secure client call.
    */
  def withDefaultSslContext: BlazeClientBuilder[F] =
    copy(sslContext = None)

  @deprecated("Use `withSslContext` or `withDefaultSslContext`", "0.20.2")
  def withSslContextOption(sslContext: Option[SSLContext]): BlazeClientBuilder[F] =
    copy(sslContext = sslContext)

  @deprecated("Use `withDefaultSslContext`", "0.20.2")
  def withoutSslContext: BlazeClientBuilder[F] =
    withDefaultSslContext

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

  def withScheduler(scheduler: Option[TickWheelExecutor]): BlazeClientBuilder[F] =
    copy(scheduler = scheduler)

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
    schedulerResource.flatMap { scheduler =>
      connectionManager(scheduler).map { manager =>
        BlazeClient.makeClient(
          manager = manager,
          responseHeaderTimeout = responseHeaderTimeout,
          idleTimeout = idleTimeout,
          requestTimeout = requestTimeout,
          scheduler = scheduler,
          ec = executionContext
        )
      }
    }

  private def schedulerResource: Resource[F, TickWheelExecutor] =
    scheduler match {
      case Some(s) => Resource.pure[F, TickWheelExecutor](s)
      case None => tickWheelResource
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
  def apply[F[_]: ConcurrentEffect](
      executionContext: ExecutionContext,
      sslContext: Option[SSLContext] = None): BlazeClientBuilder[F] =
    new BlazeClientBuilder[F](
      responseHeaderTimeout = 10.seconds,
      idleTimeout = 1.minute,
      requestTimeout = 1.minute,
      connectTimeout = 10.seconds,
      userAgent = Some(`User-Agent`(AgentProduct("http4s-blaze", Some(BuildInfo.version)))),
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
      scheduler = None,
      asynchronousChannelGroup = None,
      channelOptions = ChannelOptions(Vector.empty)
    ) {}
}
