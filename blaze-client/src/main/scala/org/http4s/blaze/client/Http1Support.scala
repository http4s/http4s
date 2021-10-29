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

import cats.effect._
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.pipeline.Command
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.IdleTimeoutStage
import org.http4s.blazecore.util.fromFutureNoShift
import org.http4s.client.ConnectionFailure
import org.http4s.client.RequestKey
import org.http4s.headers.`User-Agent`
import org.http4s.internal.SSLContextOption

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support[F[_]](
    sslContextOption: SSLContextOption,
    bufferSize: Int,
    asynchronousChannelGroup: Option[AsynchronousChannelGroup],
    executionContext: ExecutionContext,
    scheduler: TickWheelExecutor,
    checkEndpointIdentification: Boolean,
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    chunkBufferMaxSize: Int,
    parserMode: ParserMode,
    userAgent: Option[`User-Agent`],
    channelOptions: ChannelOptions,
    connectTimeout: Duration,
    idleTimeout: Duration,
    getAddress: RequestKey => Either[Throwable, InetSocketAddress]
)(implicit F: ConcurrentEffect[F]) {
  private val connectionManager = new ClientChannelFactory(
    bufferSize,
    asynchronousChannelGroup,
    channelOptions,
    scheduler,
    connectTimeout
  )

  def makeClient(requestKey: RequestKey): F[BlazeConnection[F]] =
    getAddress(requestKey) match {
      case Right(a) => fromFutureNoShift(F.delay(buildPipeline(requestKey, a)))
      case Left(t) => F.raiseError(t)
    }

  private def buildPipeline(
      requestKey: RequestKey,
      addr: InetSocketAddress): Future[BlazeConnection[F]] =
    connectionManager
      .connect(addr)
      .transformWith {
        case Success(head) =>
          buildStages(requestKey, head) match {
            case Right(connection) =>
              Future.successful {
                head.inboundCommand(Command.Connected)
                connection
              }
            case Left(e) =>
              Future.failed(new ConnectionFailure(requestKey, addr, e))
          }
        case Failure(e) => Future.failed(new ConnectionFailure(requestKey, addr, e))
      }(executionContext)

  private def buildStages(
      requestKey: RequestKey,
      head: HeadStage[ByteBuffer]): Either[IllegalStateException, BlazeConnection[F]] = {

    val idleTimeoutStage: Option[IdleTimeoutStage[ByteBuffer]] = makeIdleTimeoutStage()
    val ssl: Either[IllegalStateException, Option[SSLStage]] = makeSslStage(requestKey)

    val connection = new Http1Connection(
      requestKey = requestKey,
      executionContext = executionContext,
      maxResponseLineSize = maxResponseLineSize,
      maxHeaderLength = maxHeaderLength,
      maxChunkSize = maxChunkSize,
      chunkBufferMaxSize = chunkBufferMaxSize,
      parserMode = parserMode,
      userAgent = userAgent,
      idleTimeoutStage = idleTimeoutStage
    )

    ssl.map { sslStage =>
      val builder1 = LeafBuilder(connection)
      val builder2 = idleTimeoutStage.fold(builder1)(builder1.prepend(_))
      val builder3 = sslStage.fold(builder2)(builder2.prepend(_))
      builder3.base(head)

      connection
    }
  }

  private def makeIdleTimeoutStage(): Option[IdleTimeoutStage[ByteBuffer]] =
    idleTimeout match {
      case d: FiniteDuration =>
        Some(new IdleTimeoutStage[ByteBuffer](d, scheduler, executionContext))
      case _ => None
    }

  private def makeSslStage(
      requestKey: RequestKey): Either[IllegalStateException, Option[SSLStage]] =
    requestKey match {
      case RequestKey(Uri.Scheme.https, auth) =>
        val maybeSSLContext: Option[SSLContext] =
          SSLContextOption.toMaybeSSLContext(sslContextOption)

        maybeSSLContext match {
          case Some(sslContext) =>
            val eng = sslContext.createSSLEngine(auth.host.value, auth.port.getOrElse(443))
            eng.setUseClientMode(true)

            if (checkEndpointIdentification) {
              val sslParams = eng.getSSLParameters
              sslParams.setEndpointIdentificationAlgorithm("HTTPS")
              eng.setSSLParameters(sslParams)
            }

            Right(Some(new SSLStage(eng)))

          case None =>
            Left(new IllegalStateException(
              "No SSLContext configured for this client. Try `withSslContext` on the `BlazeClientBuilder`, or do not make https calls."))
        }

      case _ =>
        Right(None)
    }
}
