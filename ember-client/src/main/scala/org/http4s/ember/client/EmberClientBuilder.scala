/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.client

import io.chrisdavenport.keypool._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats._
import cats.implicits._
import cats.effect._
import scala.concurrent.duration._
import org.http4s.client._
import fs2.io.tcp.SocketGroup
import fs2.io.tcp.SocketOptionMapping
import fs2.io.tls._
import scala.concurrent.duration.Duration
import org.http4s.headers.{AgentProduct, `User-Agent`}

final class EmberClientBuilder[F[_]: Concurrent: Timer: ContextShift] private (
    private val blockerOpt: Option[Blocker],
    private val tlsContextOpt: Option[TLSContext],
    private val sgOpt: Option[SocketGroup],
    val maxTotal: Int,
    val maxPerKey: RequestKey => Int,
    val idleTimeInPool: Duration,
    private val logger: Logger[F],
    val chunkSize: Int,
    val maxResponseHeaderSize: Int,
    val timeout: Duration,
    val additionalSocketOptions: List[SocketOptionMapping[_]],
    val userAgent: Option[`User-Agent`]
) { self =>

  private def copy(
      blockerOpt: Option[Blocker] = self.blockerOpt,
      tlsContextOpt: Option[TLSContext] = self.tlsContextOpt,
      sgOpt: Option[SocketGroup] = self.sgOpt,
      maxTotal: Int = self.maxTotal,
      maxPerKey: RequestKey => Int = self.maxPerKey,
      idleTimeInPool: Duration = self.idleTimeInPool,
      logger: Logger[F] = self.logger,
      chunkSize: Int = self.chunkSize,
      maxResponseHeaderSize: Int = self.maxResponseHeaderSize,
      timeout: Duration = self.timeout,
      additionalSocketOptions: List[SocketOptionMapping[_]] = self.additionalSocketOptions,
      userAgent: Option[`User-Agent`] = self.userAgent
  ): EmberClientBuilder[F] =
    new EmberClientBuilder[F](
      blockerOpt = blockerOpt,
      tlsContextOpt = tlsContextOpt,
      sgOpt = sgOpt,
      maxTotal = maxTotal,
      maxPerKey = maxPerKey,
      idleTimeInPool = idleTimeInPool,
      logger = logger,
      chunkSize = chunkSize,
      maxResponseHeaderSize = maxResponseHeaderSize,
      timeout = timeout,
      additionalSocketOptions = additionalSocketOptions,
      userAgent = userAgent
    )

  def withTLSContext(tlsContext: TLSContext) =
    copy(tlsContextOpt = tlsContext.some)
  def withoutTLSContext = copy(tlsContextOpt = None)

  def withBlocker(blocker: Blocker) =
    copy(blockerOpt = blocker.some)

  def withSocketGroup(sg: SocketGroup) = copy(sgOpt = sg.some)

  def withMaxTotal(maxTotal: Int) = copy(maxTotal = maxTotal)
  def withMaxPerKey(maxPerKey: RequestKey => Int) = copy(maxPerKey = maxPerKey)
  def withIdleTimeInPool(idleTimeInPool: Duration) = copy(idleTimeInPool = idleTimeInPool)

  def withLogger(logger: Logger[F]) = copy(logger = logger)
  def withChunkSize(chunkSize: Int) = copy(chunkSize = chunkSize)
  def withMaxResponseHeaderSize(maxResponseHeaderSize: Int) =
    copy(maxResponseHeaderSize = maxResponseHeaderSize)
  def withTimeout(timeout: Duration) = copy(timeout = timeout)
  def withAdditionalSocketOptions(additionalSocketOptions: List[SocketOptionMapping[_]]) =
    copy(additionalSocketOptions = additionalSocketOptions)

  def withUserAgent(userAgent: `User-Agent`) =
    copy(userAgent = userAgent.some)
  def withoutUserAgent =
    copy(userAgent = None)

  def build: Resource[F, Client[F]] =
    for {
      blocker <- blockerOpt.fold(Blocker[F])(_.pure[Resource[F, *]])
      sg <- sgOpt.fold(SocketGroup[F](blocker))(_.pure[Resource[F, *]])
      tlsContextOptWithDefault <- Resource.liftF(
        tlsContextOpt
          .fold(TLSContext.system(blocker).attempt.map(_.toOption))(_.some.pure[F])
      )
      builder =
        KeyPoolBuilder
          .apply[F, RequestKey, (RequestKeySocket[F], F[Unit])](
            (requestKey: RequestKey) =>
              org.http4s.ember.client.internal.ClientHelpers
                .requestKeyToSocketWithKey[F](
                  requestKey,
                  tlsContextOptWithDefault,
                  sg,
                  additionalSocketOptions
                )
                .allocated <* logger.trace(s"Created Connection - RequestKey: ${requestKey}"),
            {
              case (RequestKeySocket(socket, r), shutdown) =>
                logger.trace(s"Shutting Down Connection - RequestKey: ${r}") >>
                  socket.endOfInput.attempt.void >>
                  socket.endOfOutput.attempt.void >>
                  socket.close.attempt.void >>
                  shutdown
            }
          )
          .withDefaultReuseState(Reusable.DontReuse)
          .withIdleTimeAllowedInPool(idleTimeInPool)
          .withMaxPerKey(maxPerKey)
          .withMaxTotal(maxTotal)
          .withOnReaperException(_ => Applicative[F].unit)
      pool <- builder.build
    } yield {
      val client = Client[F](request =>
        for {
          managed <- pool.take(RequestKey.fromRequest(request))
          _ <- Resource.liftF(
            pool.state.flatMap { poolState =>
              logger.trace(
                s"Connection Taken - Key: ${managed.value._1.requestKey} - Reused: ${managed.isReused} - PoolState: $poolState"
              )
            }
          )
          responseResource <-
            org.http4s.ember.client.internal.ClientHelpers
              .request[F](
                request,
                managed.value._1,
                managed.canBeReused,
                chunkSize,
                maxResponseHeaderSize,
                timeout,
                userAgent
              )(logger)
        } yield responseResource)
      new EmberClient[F](client, pool)
    }
}

object EmberClientBuilder {

  def default[F[_]: Concurrent: Timer: ContextShift] =
    new EmberClientBuilder[F](
      blockerOpt = None,
      tlsContextOpt = None,
      sgOpt = None,
      maxTotal = Defaults.maxTotal,
      maxPerKey = Defaults.maxPerKey,
      idleTimeInPool = Defaults.idleTimeInPool,
      logger = Slf4jLogger.getLogger[F],
      chunkSize = Defaults.chunkSize,
      maxResponseHeaderSize = Defaults.maxResponseHeaderSize,
      timeout = Defaults.timeout,
      additionalSocketOptions = Defaults.additionalSocketOptions,
      userAgent = Defaults.userAgent
    )

  private object Defaults {
    val acgFixedThreadPoolSize: Int = 100
    val chunkSize: Int = 32 * 1024
    val maxResponseHeaderSize: Int = 4096
    val timeout: Duration = 60.seconds

    // Pool Settings
    val maxPerKey = { (_: RequestKey) =>
      100
    }
    val maxTotal = 100
    val idleTimeInPool = 30.seconds // 30 Seconds in Nanos
    val additionalSocketOptions = List.empty[SocketOptionMapping[_]]
    val userAgent = Some(
      `User-Agent`(AgentProduct("http4s-ember", Some(org.http4s.BuildInfo.version))))
  }
}
