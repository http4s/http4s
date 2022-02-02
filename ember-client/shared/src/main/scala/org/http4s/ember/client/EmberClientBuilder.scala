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
import fs2.io.net.Network
import fs2.io.net.SocketGroup
import fs2.io.net.SocketOption
import fs2.io.net.tls._
import fs2.io.net.unixsocket._
import org.http4s.ProductId
import org.http4s.Request
import org.http4s.Response
import org.http4s.client._
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.ember.client.internal.ClientHelpers
import org.http4s.ember.core.h2.H2Client
import org.http4s.ember.core.h2.H2Frame.Settings.ConnectionSettings.default
import org.http4s.headers.`User-Agent`
import org.typelevel.keypool._
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

final class EmberClientBuilder[F[_]: Async] private (
    private val tlsContextOpt: Option[TLSContext[F]],
    private val sgOpt: Option[SocketGroup[F]],
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
    val retryPolicy: RetryPolicy[F],
    private val unixSockets: Option[UnixSockets[F]],
    private val enableHttp2: Boolean,
    private val pushPromiseSupport: Option[
      (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]]
    ],
) { self =>

  private def copy(
      tlsContextOpt: Option[TLSContext[F]] = self.tlsContextOpt,
      sgOpt: Option[SocketGroup[F]] = self.sgOpt,
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
      retryPolicy: RetryPolicy[F] = self.retryPolicy,
      unixSockets: Option[UnixSockets[F]] = self.unixSockets,
      enableHttp2: Boolean = self.enableHttp2,
      pushPromiseSupport: Option[
        (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]]
      ] = self.pushPromiseSupport,
  ): EmberClientBuilder[F] =
    new EmberClientBuilder[F](
      tlsContextOpt = tlsContextOpt,
      sgOpt = sgOpt,
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
      retryPolicy = retryPolicy,
      unixSockets = unixSockets,
      enableHttp2 = enableHttp2,
      pushPromiseSupport = pushPromiseSupport,
    )

  def withTLSContext(tlsContext: TLSContext[F]) =
    copy(tlsContextOpt = tlsContext.some)
  def withoutTLSContext = copy(tlsContextOpt = None)

  def withSocketGroup(sg: SocketGroup[F]) = copy(sgOpt = sg.some)

  def withMaxTotal(maxTotal: Int) = copy(maxTotal = maxTotal)
  def withMaxPerKey(maxPerKey: RequestKey => Int) = copy(maxPerKey = maxPerKey)
  def withIdleTimeInPool(idleTimeInPool: Duration) = copy(idleTimeInPool = idleTimeInPool)
  def withIdleConnectionTime(idleConnectionTime: Duration) =
    copy(idleConnectionTime = idleConnectionTime)

  def withLogger(logger: Logger[F]) = copy(logger = logger)
  def withChunkSize(chunkSize: Int) = copy(chunkSize = chunkSize)
  def withMaxResponseHeaderSize(maxResponseHeaderSize: Int) =
    copy(maxResponseHeaderSize = maxResponseHeaderSize)

  def withTimeout(timeout: Duration) = copy(timeout = timeout)
  def withAdditionalSocketOptions(additionalSocketOptions: List[SocketOption]) =
    copy(additionalSocketOptions = additionalSocketOptions)

  def withUserAgent(userAgent: `User-Agent`) =
    copy(userAgent = userAgent.some)
  def withoutUserAgent =
    copy(userAgent = None)

  def withCheckEndpointAuthentication(checkEndpointIdentification: Boolean) =
    copy(checkEndpointIdentification = checkEndpointIdentification)

  def withoutCheckEndpointAuthentication = copy(checkEndpointIdentification = false)

  def withRetryPolicy(retryPolicy: RetryPolicy[F]) =
    copy(retryPolicy = retryPolicy)

  def withUnixSockets(unixSockets: UnixSockets[F]) =
    copy(unixSockets = Some(unixSockets))

  def withHttp2 = copy(enableHttp2 = true)
  def withoutHttp2 = copy(enableHttp2 = false)

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
    */
  def withPushPromiseSupport(
      f: (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]]
  ) =
    copy(pushPromiseSupport = f.some)
  def withoutPushPromiseSupport =
    copy(pushPromiseSupport = None)

  def build: Resource[F, Client[F]] =
    for {
      sg <- Resource.pure(sgOpt.getOrElse(Network[F]))
      tlsContextOptWithDefault <- Resource.eval(
        tlsContextOpt.fold(Network[F].tlsContext.system.attempt.map(_.toOption))(_.some.pure[F])
      )
      builder =
        KeyPool.Builder
          .apply[F, RequestKey, EmberConnection[F]](
            (requestKey: RequestKey) =>
              EmberConnection(
                org.http4s.ember.client.internal.ClientHelpers
                  .requestKeyToSocketWithKey[F](
                    requestKey,
                    tlsContextOptWithDefault,
                    checkEndpointIdentification,
                    sg,
                    additionalSocketOptions,
                  )
              ) <* logger.trace(s"Created Connection - RequestKey: ${requestKey}"),
            (connection: EmberConnection[F]) =>
              logger.trace(
                s"Shutting Down Connection - RequestKey: ${connection.keySocket.requestKey}"
              ) >>
                connection.cleanup,
          )
          .withDefaultReuseState(Reusable.DontReuse)
          .withIdleTimeAllowedInPool(idleTimeInPool)
          .withMaxPerKey(maxPerKey)
          .withMaxTotal(maxTotal)
          .withOnReaperException(_ => Applicative[F].unit)
      pool <- builder.build
      optH2 <- (Alternative[Option].guard(enableHttp2) >> tlsContextOptWithDefault).traverse(
        context =>
          H2Client.impl[F](
            pushPromiseSupport.getOrElse { case (_, _) => Applicative[F].pure(Outcome.canceled) },
            context,
            logger,
            if (pushPromiseSupport.isDefined) default
            else {
              default.copy(enablePush =
                org.http4s.ember.core.h2.H2Frame.Settings.SettingsEnablePush(false)
              )
            },
          )
      )
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
          responseResource <- Resource.makeCase(
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
          ) { case ((response, drain), exitCase) =>
            exitCase match {
              case Resource.ExitCase.Succeeded =>
                ClientHelpers.postProcessResponse(
                  request,
                  response,
                  drain,
                  managed.value.nextBytes,
                  managed.canBeReused,
                )
              case _ => Applicative[F].unit
            }
          }
        } yield responseResource._1

      def unixSocketClient(
          request: Request[F],
          address: UnixSocketAddress,
      ): Resource[F, Response[F]] =
        Resource
          .eval(ApplicativeThrow[F].catchNonFatal(unixSockets.getOrElse(UnixSockets.forAsync[F])))
          .flatMap(unixSockets =>
            Resource
              .make(
                EmberConnection(
                  ClientHelpers.unixSocket(request, unixSockets, address, tlsContextOpt)
                )
              )(ec => ec.shutdown)
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
          .lookup(Request.Keys.UnixSocketAddress)
          .fold(webClient(request)) { (address: UnixSocketAddress) =>
            unixSocketClient(request, address)
          }
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

  def default[F[_]: Async] =
    new EmberClientBuilder[F](
      tlsContextOpt = None,
      sgOpt = None,
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
      retryPolicy = Defaults.retryPolicy,
      unixSockets = None,
      enableHttp2 = false,
      pushPromiseSupport = None,
    )

  private object Defaults {
    val acgFixedThreadPoolSize: Int = 100
    val chunkSize: Int = 32 * 1024
    val maxResponseHeaderSize: Int = 4096
    val idleConnectionTime = org.http4s.client.defaults.RequestTimeout
    val timeout: Duration = org.http4s.client.defaults.RequestTimeout

    // Pool Settings
    val maxPerKey = { (_: RequestKey) =>
      100
    }
    val maxTotal = 100
    val idleTimeInPool = 30.seconds // 30 Seconds in Nanos
    val additionalSocketOptions = List.empty[SocketOption]
    val userAgent = Some(
      `User-Agent`(ProductId("http4s-ember", Some(org.http4s.BuildInfo.version)))
    )

    def retryPolicy[F[_]]: RetryPolicy[F] = ClientHelpers.RetryLogic.retryUntilFresh
  }
}
