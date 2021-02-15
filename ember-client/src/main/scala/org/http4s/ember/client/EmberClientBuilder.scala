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
import cats.syntax.all._
import cats.effect._

import scala.concurrent.duration._
import org.http4s.ProductId
import org.http4s.client._
import org.typelevel.keypool._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import fs2.io.net.SocketGroup
import fs2.io.net.SocketOption
import fs2.io.net.tls._
import fs2.io.net.Network

import scala.concurrent.duration.Duration
import org.http4s.headers.{`User-Agent`}
import org.http4s.ember.client.internal.ClientHelpers

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
    val userAgent: Option[`User-Agent`]
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
      userAgent: Option[`User-Agent`] = self.userAgent
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
      userAgent = userAgent
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

  def build: Resource[F, Client[F]] =
    for {
      sg <- sgOpt.fold(Network.forAsync.socketGroup())(_.pure[Resource[F, *]])
      tlsContextOptWithDefault <- Resource.eval(
        tlsContextOpt
          .fold(TLSContext.Builder.forAsync.system.attempt.map(_.toOption))(_.some.pure[F])
      )
      builder =
        KeyPoolBuilder
          .apply[F, RequestKey, EmberConnection[F]](
            (requestKey: RequestKey) =>
              EmberConnection(
                org.http4s.ember.client.internal.ClientHelpers
                  .requestKeyToSocketWithKey[F](
                    requestKey,
                    tlsContextOptWithDefault,
                    sg,
                    additionalSocketOptions
                  )) <* logger.trace(s"Created Connection - RequestKey: ${requestKey}"),
            { case connection =>
              logger.trace(
                s"Shutting Down Connection - RequestKey: ${connection.keySocket.requestKey}") >>
                connection.cleanup
            }
          )
          .withDefaultReuseState(Reusable.DontReuse)
          .withIdleTimeAllowedInPool(idleTimeInPool)
          .withMaxPerKey(maxPerKey)
          .withMaxTotal(maxTotal)
          .withOnReaperException(_ => Applicative[F].unit)
      pool <- builder.build
    } yield {
      val client = Client[F] { request =>
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
                userAgent
              )
          ) { case ((response, drain), exitCase) =>
            exitCase match {
              case Resource.ExitCase.Succeeded =>
                ClientHelpers.postProcessResponse(
                  request,
                  response,
                  drain,
                  managed.value.nextBytes,
                  managed.canBeReused)
              case _ => Applicative[F].unit
            }
          }
        } yield responseResource._1
      }
      new EmberClient[F](client, pool)
    }
}

object EmberClientBuilder {

  def default[F[_]: Async] =
    new EmberClientBuilder[F](
      tlsContextOpt = None,
      sgOpt = None,
      maxTotal = Defaults.maxTotal,
      maxPerKey = Defaults.maxPerKey,
      idleTimeInPool = Defaults.idleTimeInPool,
      logger = Slf4jLogger.getLogger[F],
      chunkSize = Defaults.chunkSize,
      maxResponseHeaderSize = Defaults.maxResponseHeaderSize,
      idleConnectionTime = Defaults.idleConnectionTime,
      timeout = Defaults.timeout,
      additionalSocketOptions = Defaults.additionalSocketOptions,
      userAgent = Defaults.userAgent
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
      `User-Agent`(ProductId("http4s-ember", Some(org.http4s.BuildInfo.version))))
  }
}
