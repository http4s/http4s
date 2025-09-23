/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client

import cats._
import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s.UnixSocketAddress
import fs2.io.net.Network
import fs2.io.net.SocketGroup
import fs2.io.net.SocketOption
import fs2.io.net.tls._
import fs2.io.net.unixsocket.UnixSockets
import org.http4s.ProductId
import org.http4s.Request
import org.http4s.Response
import org.http4s.client._
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.ember.client.internal.ClientHelpers
import org.http4s.ember.core.h2.H2Client
import org.http4s.ember.core.h2.H2Frame
import org.http4s.ember.core.h2.H2Frame.Settings.ConnectionSettings.default
import org.http4s.headers.`User-Agent`
import org.typelevel.keypool._
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

final class EmberClientBuilder[F[_]: Async: Network] private (
    private val tlsContextOpt: Option[TLSContext[F]],
    val maxTotal: Int,
    val maxPerKey: RequestKey => Int,
    val idleTimeInPool: Duration,
    private val logger: Logger[F],
    val chunkSize: Int,
    val maxResponseHeaderSize: Int,
    private val idleConnectionTime: Duration,
    val timeout: Duration,
    val additionalSocketOptions: List[SocketOption],
    val userAgent: Option[`User-Agent`],
    val checkEndpointIdentification: Boolean,
    val serverNameIndication: Boolean,
    val retryPolicy: RetryPolicy[F],
    private val enableHttp2: Boolean,
    private val pushPromiseSupport: Option[
      (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]]
    ],
) extends EmberClientBuilderPlatform { self =>

  private def copy(
      tlsContextOpt: Option[TLSContext[F]] = self.tlsContextOpt,
      maxTotal: Int = self.maxTotal,
      maxPerKey: RequestKey => Int = self.maxPerKey,
      idleTimeInPool: Duration = self.idleTimeInPool,
      logger: Logger[F] = self.logger,
      chunkSize: Int = self.chunkSize,
      maxResponseHeaderSize: Int = self.maxResponseHeaderSize,
      idleConnectionTime: Duration = self.idleConnectionTime,
      timeout: Duration = self.timeout,
      additionalSocketOptions: List[SocketOption] = self.additionalSocketOptions,
      userAgent: Option[`User-Agent`] = self.userAgent,
      checkEndpointIdentification: Boolean = self.checkEndpointIdentification,
      serverNameIndication: Boolean = self.serverNameIndication,
      retryPolicy: RetryPolicy[F] = self.retryPolicy,
      enableHttp2: Boolean = self.enableHttp2,
      pushPromiseSupport: Option[
        (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]]
      ] = self.pushPromiseSupport,
  ): EmberClientBuilder[F] =
    new EmberClientBuilder[F](
      tlsContextOpt = tlsContextOpt,
      maxTotal = maxTotal,
      maxPerKey = maxPerKey,
      idleTimeInPool = idleTimeInPool,
      logger = logger,
      chunkSize = chunkSize,
      maxResponseHeaderSize = maxResponseHeaderSize,
      idleConnectionTime = idleConnectionTime,
      timeout = timeout,
      additionalSocketOptions = additionalSocketOptions,
      userAgent = userAgent,
      checkEndpointIdentification = checkEndpointIdentification,
      serverNameIndication = serverNameIndication,
      retryPolicy = retryPolicy,
      enableHttp2 = enableHttp2,
      pushPromiseSupport = pushPromiseSupport,
    )

  /** Sets a custom `TLSContext`.
    * By default a `TLSContext` is created from the system default `SSLContext`.
    */
  def withTLSContext(tlsContext: TLSContext[F]): EmberClientBuilder[F] =
    copy(tlsContextOpt = tlsContext.some)

  /** Unset any `TLSContext` and creates one from the system default `SSLContext`. */
  def withoutTLSContext: EmberClientBuilder[F] = copy(tlsContextOpt = None)

  /** Sets the `SocketGroup`, a group of TCP sockets to be used in connections. */
  @deprecated("Explicit socket groups are no longer supported", "0.23.32")
  def withSocketGroup(sg: SocketGroup[F]): EmberClientBuilder[F] = this

  /** Sets the connection pool's total maximum number of idle connections.
    * Per `RequestKey` values set with `withMaxPerKey` cannot override this total maximum.
    */
  def withMaxTotal(maxTotal: Int): EmberClientBuilder[F] = copy(maxTotal = maxTotal)

  /** Sets the connection pool's maximum number of pooled connections per RequestKey. */
  def withMaxPerKey(maxPerKey: RequestKey => Int): EmberClientBuilder[F] =
    copy(maxPerKey = maxPerKey)

  /** Sets the connection pool's maximum time a connection can be idle.
    * The timeout starts when a connection is returned the the pool, and reset when it is borrowed.
    */
  def withIdleTimeInPool(idleTimeInPool: Duration): EmberClientBuilder[F] =
    copy(idleTimeInPool = idleTimeInPool)

  /** Sets the idle timeout on connections.  The timeout is reset with each read or write. */
  def withIdleConnectionTime(idleConnectionTime: Duration): EmberClientBuilder[F] =
    copy(idleConnectionTime = idleConnectionTime)

  /** Sets the `Logger`. */
  def withLogger(logger: Logger[F]): EmberClientBuilder[F] = copy(logger = logger)

  /** Sets the max `chunkSize` in bytes to read from sockets at a time. */
  def withChunkSize(chunkSize: Int): EmberClientBuilder[F] = copy(chunkSize = chunkSize)

  /** Sets the max size in bytes to read while parsing response headers. */
  def withMaxResponseHeaderSize(maxResponseHeaderSize: Int): EmberClientBuilder[F] =
    copy(maxResponseHeaderSize = maxResponseHeaderSize)

  /** Sets the header receive timeout on connections. */
  def withTimeout(timeout: Duration): EmberClientBuilder[F] = copy(timeout = timeout)

  /** Sets additional socket options to apply to the underlying sockets. */
  def withAdditionalSocketOptions(
      additionalSocketOptions: List[SocketOption]
  ): EmberClientBuilder[F] =
    copy(additionalSocketOptions = additionalSocketOptions)

  /** Sets the default User-Agent string.
    * A `User-Agent` header on a request takes priority over this setting.
    */
  def withUserAgent(userAgent: `User-Agent`): EmberClientBuilder[F] =
    copy(userAgent = userAgent.some)

  /** Clears the default User-Agent string, so no User-Agent header is sent.
    * A `User-Agent` header on a request takes priority over this setting.
    */
  def withoutUserAgent: EmberClientBuilder[F] =
    copy(userAgent = None)

  /** Sets whether or not to force endpoint authentication/verification on the `TLSContext`.
    * Enabled by default. When enabled the server's identity will be checked against the server's
    * certificate during SSL/TLS handshaking. This is important to avoid man-in-the-middle attacks
    * by confirming server identity against their certificate.
    */
  def withCheckEndpointAuthentication(checkEndpointIdentification: Boolean): EmberClientBuilder[F] =
    copy(checkEndpointIdentification = checkEndpointIdentification)

  /** Disables endpoint authentication/verification. */
  def withoutCheckEndpointAuthentication: EmberClientBuilder[F] =
    copy(checkEndpointIdentification = false)

  /** Sets whether or not to enable Server Name Indication on the `TLSContext`.
    * Enabled by default. When enabled the hostname will be indicated during SSL/TLS handshaking.
    * This is important to reach a web server that is responsible for multiple hostnames so that it
    * can use the correct certificate.
    */
  def withServerNameIndication(serverNameIndication: Boolean): EmberClientBuilder[F] =
    copy(serverNameIndication = serverNameIndication)

  /** Disables Server Name Indication */
  def withoutServerNameIndication: EmberClientBuilder[F] =
    copy(serverNameIndication = false)

  /** Sets the `RetryPolicy`. */
  def withRetryPolicy(retryPolicy: RetryPolicy[F]): EmberClientBuilder[F] =
    copy(retryPolicy = retryPolicy)

  /** Sets underlying `UnixSockets` to use for requests with a `UnixSocketAddress`.
    * Useful for secure and efficient inter-process communication.
    * See also `UnixSocket` client middleware to direct all requests to a `UnixSocketAddress`.
    */
  @deprecated("No longer needed", "0.23.32")
  def withUnixSockets(unixSockets: UnixSockets[F]): EmberClientBuilder[F] =
    this

  /** Enables HTTP/2 support. Disabled by default. */
  def withHttp2: EmberClientBuilder[F] = copy(enableHttp2 = true)

  /** Disables HTTP/2 support. Disabled by default. */
  def withoutHttp2: EmberClientBuilder[F] = copy(enableHttp2 = false)

  /** Push promises are implemented via responding with a PushPromise frame
    * which is effectively a request headers frame for a request that wasn't
    * sent by the client.
    *
    * The second param is the response once it is available that you can wait
    * for OR you can cancel the Outcome to send a termination signal to
    * ask the remote server to stop sending additional data from this data stream.
    * If you want to handle these the outcome can just be outcome successful. But
    * you can save significant data by canceling requests you don't want.
    *
    * Push promises are very useful to get all the data necessary to render a page in parallel
    * to the actual data for that page leading to much faster render times, or sending
    * additional cache enriching information.
    *
    * Push promise support is disabled by default.
    */
  def withPushPromiseSupport(
      f: (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]]
  ): EmberClientBuilder[F] =
    copy(pushPromiseSupport = f.some)

  /** Disables Push promise support.
    * Push promise support is disabled by default.
    */
  def withoutPushPromiseSupport: EmberClientBuilder[F] =
    copy(pushPromiseSupport = None)

  private val verifyTimeoutRelations: F[Unit] =
    logger
      .warn(
        s"timeout ($timeout) is >= idleConnectionTime ($idleConnectionTime). " +
          s"It is recommended to configure timeout < idleConnectionTime, " +
          s"or disable one of them explicitly by setting it to Duration.Inf."
      )
      .whenA(timeout.isFinite && timeout >= idleConnectionTime)

  def build: Resource[F, Client[F]] =
    for {
      _ <- Resource.eval(verifyTimeoutRelations)
      tlsContextOptWithDefault <-
        tlsContextOpt
          .fold(Network[F].tlsContext.systemResource.attempt.map(_.toOption))(
            _.some.pure[Resource[F, *]]
          )
      builder =
        KeyPool.Builder
          .apply[F, RequestKey, EmberConnection[F]]((requestKey: RequestKey) =>
            EmberConnection(
              org.http4s.ember.client.internal.ClientHelpers
                .requestKeyToSocketWithKey[F](
                  requestKey,
                  tlsContextOptWithDefault,
                  checkEndpointIdentification,
                  serverNameIndication,
                  additionalSocketOptions,
                ),
              chunkSize,
            ) <* Resource
              .eval(logger.trace(s"Created Connection - RequestKey: ${requestKey}"))
              .onFinalize(
                logger.trace(
                  s"Shutting Down Connection - RequestKey: ${requestKey}"
                )
              )
          )
          .withDefaultReuseState(Reusable.DontReuse)
          .withIdleTimeAllowedInPool(idleTimeInPool)
          .withMaxPerKey(maxPerKey)
          .withMaxTotal(maxTotal)
          .withOnReaperException(_ => Applicative[F].unit)
      pool <- builder.build
      optH2 <- (if (enableHttp2) tlsContextOptWithDefault else None).traverse { context =>
        H2Client.impl[F](
          pushPromiseSupport.getOrElse { case (_, _) => Applicative[F].pure(Outcome.canceled) },
          context,
          logger,
          if (pushPromiseSupport.isDefined) default
          else
            default.copy(enablePush = H2Frame.Settings.SettingsEnablePush(isEnabled = false)),
          checkEndpointIdentification,
          serverNameIndication,
        )
      }
    } yield {
      def webClient(request: Request[F]): Resource[F, Response[F]] =
        for {
          managed <- ClientHelpers.getValidManaged(pool, request)
          _ <- Resource.eval(
            pool.state.flatMap { poolState =>
              logger.trace(
                s"Connection Taken - Key: ${managed.value.keySocket.requestKey} - Reused: ${managed.isReused} - PoolState: $poolState"
              )
            }
          )
          responseResource <- Resource.makeCaseFull((poll: Poll[F]) =>
            poll(
              ClientHelpers
                .request[F](
                  request,
                  managed.value,
                  chunkSize,
                  maxResponseHeaderSize,
                  idleConnectionTime,
                  timeout,
                  userAgent,
                )
            )
          ) { case ((response, drain), exitCase) =>
            exitCase match {
              case Resource.ExitCase.Succeeded =>
                ClientHelpers.postProcessResponse(
                  request,
                  response,
                  drain,
                  managed.value.nextBytes,
                  managed.canBeReused,
                  managed.value.startNextRead,
                )
              case _ => Applicative[F].unit
            }
          }
        } yield responseResource._1

      def unixSocketClient(
          request: Request[F],
          address: UnixSocketAddress,
          enableEndpointValidation: Boolean,
          enableServerNameIndication: Boolean,
      ): Resource[F, Response[F]] =
        EmberConnection(
          ClientHelpers.unixSocket(
            request,
            address,
            tlsContextOpt,
            enableEndpointValidation,
            enableServerNameIndication,
            Nil,
          ),
          chunkSize,
        )
          .flatMap(connection =>
            Resource.eval(
              ClientHelpers
                .request[F](
                  request,
                  connection,
                  chunkSize,
                  maxResponseHeaderSize,
                  idleConnectionTime,
                  timeout,
                  userAgent,
                )
                .map(_._1)
            )
          )
      val client = Client[F] { request =>
        request.attributes
          .lookup(Request.Keys.ForcedUnixSocketAddress)
          .fold(webClient(request))(
            unixSocketClient(request, _, checkEndpointIdentification, serverNameIndication)
          )
      }
      val stackClient = Retry.create(retryPolicy, logRetries = false)(client)
      val iClient = new EmberClient[F](stackClient, pool)

      optH2.fold(iClient) { h2 =>
        val h2Client = Client(h2(iClient.run))
        new EmberClient(h2Client, pool)
      }
    }
}

object EmberClientBuilder extends EmberClientBuilderCompanionPlatform {

  def default[F[_]: Async: Network] =
    new EmberClientBuilder[F](
      tlsContextOpt = None,
      maxTotal = Defaults.maxTotal,
      maxPerKey = Defaults.maxPerKey,
      idleTimeInPool = Defaults.idleTimeInPool,
      logger = defaultLogger[F],
      chunkSize = Defaults.chunkSize,
      maxResponseHeaderSize = Defaults.maxResponseHeaderSize,
      idleConnectionTime = Defaults.idleConnectionTime,
      timeout = Defaults.timeout,
      additionalSocketOptions = Defaults.additionalSocketOptions,
      userAgent = Defaults.userAgent,
      checkEndpointIdentification = true,
      serverNameIndication = true,
      retryPolicy = Defaults.retryPolicy,
      enableHttp2 = false,
      pushPromiseSupport = None,
    )

  @deprecated("Use the overload which accepts a Network", "0.23.16")
  def default[F[_]](async: Async[F]): EmberClientBuilder[F] =
    default(async, Network.forAsync(async))

  private object Defaults {
    val acgFixedThreadPoolSize: Int = 100
    val chunkSize: Int = 32 * 1024
    val maxResponseHeaderSize: Int = 4096
    val idleConnectionTime: FiniteDuration = org.http4s.ember.core.Defaults.IdleTimeout
    val timeout: Duration = org.http4s.client.defaults.RequestTimeout

    // Pool Settings
    val maxPerKey: RequestKey => Int = { (_: RequestKey) =>
      100
    }
    val maxTotal: Int = 100
    val idleTimeInPool: FiniteDuration = 30.seconds // 30 Seconds in Nanos
    val additionalSocketOptions: List[SocketOption] = List.empty[SocketOption]
    val userAgent: Some[`User-Agent`] = Some(
      `User-Agent`(ProductId("http4s-ember", Some(org.http4s.BuildInfo.version)))
    )

    def retryPolicy[F[_]]: RetryPolicy[F] = ClientHelpers.RetryLogic.retryUntilFresh
  }
}
