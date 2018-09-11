package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import fs2.Stream
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import org.http4s.headers.{AgentProduct, `User-Agent`}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

sealed abstract class BlazeClientBuilder[F[_]] private (
    val responseHeaderTimeout: Duration,
    val idleTimeout: Duration,
    val requestTimeout: Duration,
    val userAgent: Option[`User-Agent`],
    val maxTotalConnections: Int,
    val maxWaitQueueLimit: Int,
    val maxConnectionsPerRequestKey: RequestKey => Int,
    val sslContext: Option[SSLContext],
    val checkEndpointIdentification: Boolean,
    val maxResponseLineSize: Int,
    val maxHeaderLength: Int,
    val maxChunkSize: Int,
    val parserMode: ParserMode,
    val bufferSize: Int,
    val executionContext: ExecutionContext,
    val asynchronousChannelGroup: Option[AsynchronousChannelGroup]
) {
  private def copy(
      responseHeaderTimeout: Duration = responseHeaderTimeout,
      idleTimeout: Duration = idleTimeout,
      requestTimeout: Duration = requestTimeout,
      userAgent: Option[`User-Agent`] = userAgent,
      maxTotalConnections: Int = maxTotalConnections,
      maxWaitQueueLimit: Int = maxWaitQueueLimit,
      maxConnectionsPerRequestKey: RequestKey => Int = maxConnectionsPerRequestKey,
      sslContext: Option[SSLContext] = sslContext,
      checkEndpointIdentification: Boolean = checkEndpointIdentification,
      maxResponseLineSize: Int = maxResponseLineSize,
      maxHeaderLength: Int = maxHeaderLength,
      maxChunkSize: Int = maxChunkSize,
      parserMode: ParserMode = parserMode,
      bufferSize: Int = bufferSize,
      executionContext: ExecutionContext = executionContext,
      asynchronousChannelGroup: Option[AsynchronousChannelGroup] = asynchronousChannelGroup
  ): BlazeClientBuilder[F] =
    new BlazeClientBuilder[F](
      responseHeaderTimeout = responseHeaderTimeout,
      idleTimeout = idleTimeout,
      requestTimeout = requestTimeout,
      userAgent = userAgent,
      maxTotalConnections = maxTotalConnections,
      maxWaitQueueLimit = maxWaitQueueLimit,
      maxConnectionsPerRequestKey = maxConnectionsPerRequestKey,
      sslContext = sslContext,
      checkEndpointIdentification = checkEndpointIdentification,
      maxResponseLineSize = maxResponseLineSize,
      maxHeaderLength = maxHeaderLength,
      maxChunkSize = maxChunkSize,
      parserMode = parserMode,
      bufferSize = bufferSize,
      executionContext = executionContext,
      asynchronousChannelGroup = asynchronousChannelGroup
    ) {}

  def withResponseHeaderTimeout(responseHeaderTimeout: Duration): BlazeClientBuilder[F] =
    copy(responseHeaderTimeout = responseHeaderTimeout)

  def withIdleTimeout(idleTimeout: Duration): BlazeClientBuilder[F] =
    copy(idleTimeout = idleTimeout)

  def withRequestTimeout(requestTimeout: Duration): BlazeClientBuilder[F] =
    copy(requestTimeout = requestTimeout)

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

  def withSslContextOption(sslContext: Option[SSLContext]): BlazeClientBuilder[F] =
    copy(sslContext = sslContext)
  def withSslContext(sslContext: SSLContext): BlazeClientBuilder[F] =
    withSslContextOption(Some(sslContext))
  def withoutSslContext: BlazeClientBuilder[F] =
    withSslContextOption(None)

  def withCheckEndpointAuthentication(checkEndpointIdentification: Boolean): BlazeClientBuilder[F] =
    copy(checkEndpointIdentification = checkEndpointIdentification)

  def withMaxResponseLineSize(maxResponseLineSize: Int): BlazeClientBuilder[F] =
    copy(maxResponseLineSize = maxResponseLineSize)

  def withMaxChunkSize(maxChunkSize: Int): BlazeClientBuilder[F] =
    copy(maxChunkSize = maxChunkSize)

  def withParserMode(parserMode: ParserMode): BlazeClientBuilder[F] =
    copy(parserMode = parserMode)

  def withBufferSize(bufferSize: Int): BlazeClientBuilder[F] =
    copy(bufferSize = bufferSize)

  def withExecutionContext(executionContext: ExecutionContext): BlazeClientBuilder[F] =
    copy(executionContext = executionContext)

  def withAsynchronousChannelGroupOption(
      asynchronousChannelGroup: Option[AsynchronousChannelGroup]): BlazeClientBuilder[F] =
    copy(asynchronousChannelGroup = asynchronousChannelGroup)
  def withAsynchronousChannelGroup(
      asynchronousChannelGroup: AsynchronousChannelGroup): BlazeClientBuilder[F] =
    withAsynchronousChannelGroupOption(Some(asynchronousChannelGroup))
  def withoutAsynchronousChannelGroup: BlazeClientBuilder[F] =
    withAsynchronousChannelGroupOption(None)

  def resource(implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    Resource.make(
      connectionManager.map(
        manager =>
          BlazeClient.makeClient(
            manager = manager,
            responseHeaderTimeout = responseHeaderTimeout,
            idleTimeout = idleTimeout,
            requestTimeout = requestTimeout
        )))(_.shutdown)

  def stream(implicit F: ConcurrentEffect[F]): Stream[F, Client[F]] =
    Stream.resource(resource)

  private def connectionManager(
      implicit F: ConcurrentEffect[F]): F[ConnectionManager[F, BlazeConnection[F]]] = {
    val http1: ConnectionBuilder[F, BlazeConnection[F]] = new Http1Support(
      sslContextOption = sslContext,
      bufferSize = bufferSize,
      asynchronousChannelGroup = asynchronousChannelGroup,
      executionContext = executionContext,
      checkEndpointIdentification = checkEndpointIdentification,
      maxResponseLineSize = maxResponseLineSize,
      maxHeaderLength = maxHeaderLength,
      maxChunkSize = maxChunkSize,
      parserMode = parserMode,
      userAgent = userAgent
    ).makeClient
    ConnectionManager
      .pool(
        builder = http1,
        maxTotal = maxTotalConnections,
        maxWaitQueueLimit = maxWaitQueueLimit,
        maxConnectionsPerRequestKey = maxConnectionsPerRequestKey,
        responseHeaderTimeout = responseHeaderTimeout,
        requestTimeout = requestTimeout,
        executionContext = executionContext
      )
  }
}

object BlazeClientBuilder {
  def apply[F[_]](
      executionContext: ExecutionContext,
      sslContext: Option[SSLContext] = Some(SSLContext.getDefault)): BlazeClientBuilder[F] =
    new BlazeClientBuilder[F](
      responseHeaderTimeout = 10.seconds,
      idleTimeout = 1.minute,
      requestTimeout = Duration.Inf,
      userAgent = Some(`User-Agent`(AgentProduct("http4s-blaze", Some(BuildInfo.version)))),
      maxTotalConnections = 10,
      maxWaitQueueLimit = 256,
      maxConnectionsPerRequestKey = Function.const(256),
      sslContext = sslContext,
      checkEndpointIdentification = true,
      maxResponseLineSize = 4096,
      maxHeaderLength = 40960,
      maxChunkSize = Int.MaxValue,
      parserMode = ParserMode.Strict,
      bufferSize = 8192,
      executionContext = executionContext,
      asynchronousChannelGroup = None
    ) {}
}
