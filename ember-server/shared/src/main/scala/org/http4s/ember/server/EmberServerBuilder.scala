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

package org.http4s.ember.server

import _root_.org.typelevel.log4cats.Logger
import cats._
import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.io.net.Network
import fs2.io.net.SocketGroup
import fs2.io.net.SocketOption
import fs2.io.net.tls._
import org.http4s._
import org.http4s.ember.server.internal.ServerHelpers
import org.http4s.ember.server.internal.Shutdown
import org.http4s.server.Server

import scala.concurrent.duration._

final class EmberServerBuilder[F[_]: Async] private (
    val host: Option[Host],
    val port: Port,
    private val httpApp: HttpApp[F],
    private val tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
    private val sgOpt: Option[SocketGroup[F]],
    private val errorHandler: Throwable => F[Response[F]],
    private val onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
    val maxConcurrency: Int,
    val receiveBufferSize: Int,
    val maxHeaderSize: Int,
    val requestHeaderReceiveTimeout: Duration,
    val idleTimeout: Duration,
    val shutdownTimeout: Duration,
    val additionalSocketOptions: List[SocketOption],
    private val logger: Logger[F]
) { self =>

  private def copy(
      host: Option[Host] = self.host,
      port: Port = self.port,
      httpApp: HttpApp[F] = self.httpApp,
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)] = self.tlsInfoOpt,
      sgOpt: Option[SocketGroup[F]] = self.sgOpt,
      errorHandler: Throwable => F[Response[F]] = self.errorHandler,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit] = self.onWriteFailure,
      maxConcurrency: Int = self.maxConcurrency,
      receiveBufferSize: Int = self.receiveBufferSize,
      maxHeaderSize: Int = self.maxHeaderSize,
      requestHeaderReceiveTimeout: Duration = self.requestHeaderReceiveTimeout,
      idleTimeout: Duration = self.idleTimeout,
      shutdownTimeout: Duration = self.shutdownTimeout,
      additionalSocketOptions: List[SocketOption] = self.additionalSocketOptions,
      logger: Logger[F] = self.logger
  ): EmberServerBuilder[F] =
    new EmberServerBuilder[F](
      host = host,
      port = port,
      httpApp = httpApp,
      tlsInfoOpt = tlsInfoOpt,
      sgOpt = sgOpt,
      errorHandler = errorHandler,
      onWriteFailure = onWriteFailure,
      maxConcurrency = maxConcurrency,
      receiveBufferSize = receiveBufferSize,
      maxHeaderSize = maxHeaderSize,
      requestHeaderReceiveTimeout = requestHeaderReceiveTimeout,
      idleTimeout = idleTimeout,
      shutdownTimeout = shutdownTimeout,
      additionalSocketOptions = additionalSocketOptions,
      logger = logger
    )

  def withHostOption(host: Option[Host]) = copy(host = host)
  def withHost(host: Host) = withHostOption(Some(host))
  def withoutHost = withHostOption(None)

  def withPort(port: Port) = copy(port = port)
  def withHttpApp(httpApp: HttpApp[F]) = copy(httpApp = httpApp)

  def withSocketGroup(sg: SocketGroup[F]) =
    copy(sgOpt = sg.pure[Option])

  def withTLS(tlsContext: TLSContext[F], tlsParameters: TLSParameters = TLSParameters.Default) =
    copy(tlsInfoOpt = (tlsContext, tlsParameters).pure[Option])
  def withoutTLS =
    copy(tlsInfoOpt = None)

  def withIdleTimeout(idleTimeout: Duration) =
    copy(idleTimeout = idleTimeout)

  def withShutdownTimeout(shutdownTimeout: Duration) =
    copy(shutdownTimeout = shutdownTimeout)

  @deprecated("0.21.17", "Use withErrorHandler - Do not allow the F to fail")
  def withOnError(onError: Throwable => Response[F]) =
    withErrorHandler({ case e => onError(e).pure[F] })

  def withErrorHandler(errorHandler: PartialFunction[Throwable, F[Response[F]]]) =
    copy(errorHandler = errorHandler)

  def withOnWriteFailure(onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit]) =
    copy(onWriteFailure = onWriteFailure)
  def withMaxConcurrency(maxConcurrency: Int) = copy(maxConcurrency = maxConcurrency)
  def withReceiveBufferSize(receiveBufferSize: Int) = copy(receiveBufferSize = receiveBufferSize)
  def withMaxHeaderSize(maxHeaderSize: Int) = copy(maxHeaderSize = maxHeaderSize)
  def withRequestHeaderReceiveTimeout(requestHeaderReceiveTimeout: Duration) =
    copy(requestHeaderReceiveTimeout = requestHeaderReceiveTimeout)
  def withLogger(l: Logger[F]) = copy(logger = l)

  def build: Resource[F, Server] =
    for {
      sg <- sgOpt.getOrElse(Network[F]).pure[Resource[F, *]]
      ready <- Resource.eval(Deferred[F, Either[Throwable, SocketAddress[IpAddress]]])
      shutdown <- Resource.eval(Shutdown[F](shutdownTimeout))
      _ <- Concurrent[F].background(
        ServerHelpers
          .server(
            host,
            port,
            additionalSocketOptions,
            sg,
            httpApp,
            tlsInfoOpt,
            ready,
            shutdown,
            errorHandler,
            onWriteFailure,
            maxConcurrency,
            receiveBufferSize,
            maxHeaderSize,
            requestHeaderReceiveTimeout,
            idleTimeout,
            logger
          )
          .compile
          .drain
      )
      _ <- Resource.make(Applicative[F].unit)(_ => shutdown.await)
      bindAddress <- Resource.eval(ready.get.rethrow)
      _ <- Resource.eval(logger.info(s"Ember-Server service bound to address: ${bindAddress}"))
    } yield new Server {
      val address = bindAddress
      val isSecure = tlsInfoOpt.isDefined
    }
}

object EmberServerBuilder extends EmberServerBuilderCompanionPlatform {
  def default[F[_]: Async]: EmberServerBuilder[F] =
    new EmberServerBuilder[F](
      host = Host.fromString(Defaults.host),
      port = Port.fromInt(Defaults.port).get,
      httpApp = Defaults.httpApp[F],
      tlsInfoOpt = None,
      sgOpt = None,
      errorHandler = Defaults.errorHandler[F],
      onWriteFailure = Defaults.onWriteFailure[F],
      maxConcurrency = Defaults.maxConcurrency,
      receiveBufferSize = Defaults.receiveBufferSize,
      maxHeaderSize = Defaults.maxHeaderSize,
      requestHeaderReceiveTimeout = Defaults.requestHeaderReceiveTimeout,
      idleTimeout = Defaults.idleTimeout,
      shutdownTimeout = Defaults.shutdownTimeout,
      additionalSocketOptions = Defaults.additionalSocketOptions,
      logger = defaultLogger[F]
    )

  private object Defaults {
    val host: String = server.defaults.IPv4Host
    val port: Int = server.defaults.HttpPort

    def httpApp[F[_]: Applicative]: HttpApp[F] = HttpApp.notFound[F]

    private val serverFailure =
      Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)
    // Effectful Handler - Perhaps a Logger
    // Will only arrive at this code if your HttpApp fails or the request receiving fails for some reason
    def errorHandler[F[_]: Applicative]: Throwable => F[Response[F]] = { case (_: Throwable) =>
      serverFailure.covary[F].pure[F]
    }

    @deprecated("0.21.17", "Use errorHandler, default fallback of failure InternalServerFailure")
    def onError[F[_]]: Throwable => Response[F] = { (_: Throwable) =>
      serverFailure.covary[F]
    }

    def onWriteFailure[F[_]: Applicative]
        : (Option[Request[F]], Response[F], Throwable) => F[Unit] = {
      case _: (Option[Request[F]], Response[F], Throwable) => Applicative[F].unit
    }
    val maxConcurrency: Int = server.defaults.MaxConnections
    val receiveBufferSize: Int = 256 * 1024
    val maxHeaderSize: Int = server.defaults.MaxHeadersSize
    val requestHeaderReceiveTimeout: Duration = 5.seconds
    val idleTimeout: Duration = server.defaults.IdleTimeout
    val shutdownTimeout: Duration = server.defaults.ShutdownTimeout
    val additionalSocketOptions = List.empty[SocketOption]
  }
}
