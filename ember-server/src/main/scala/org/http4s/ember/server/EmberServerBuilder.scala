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
import _root_.org.typelevel.log4cats.slf4j.Slf4jLogger
import cats._
import cats.effect._
import cats.effect.concurrent._
import cats.syntax.all._
import fs2.io.tcp.SocketGroup
import fs2.io.tcp.SocketOptionMapping
import fs2.io.tls._
import org.http4s._
import org.http4s.ember.server.internal.ServerHelpers
import org.http4s.ember.server.internal.Shutdown
import org.http4s.server.Server

import java.net.InetSocketAddress
import scala.concurrent.duration._

final class EmberServerBuilder[F[_]: Concurrent: Timer: ContextShift] private (
    val host: String,
    val port: Int,
    private val httpApp: HttpApp[F],
    private val blockerOpt: Option[Blocker],
    private val tlsInfoOpt: Option[(TLSContext, TLSParameters)],
    private val sgOpt: Option[SocketGroup],
    private val errorHandler: Throwable => F[Response[F]],
    private val onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
    val maxConnections: Int,
    val receiveBufferSize: Int,
    val maxHeaderSize: Int,
    val requestHeaderReceiveTimeout: Duration,
    val idleTimeout: Duration,
    val shutdownTimeout: Duration,
    val additionalSocketOptions: List[SocketOptionMapping[_]],
    private val logger: Logger[F],
) { self =>

  @deprecated("Use org.http4s.ember.server.EmberServerBuilder.maxConnections", "0.22.3")
  val maxConcurrency: Int = maxConnections

  private def copy(
      host: String = self.host,
      port: Int = self.port,
      httpApp: HttpApp[F] = self.httpApp,
      blockerOpt: Option[Blocker] = self.blockerOpt,
      tlsInfoOpt: Option[(TLSContext, TLSParameters)] = self.tlsInfoOpt,
      sgOpt: Option[SocketGroup] = self.sgOpt,
      errorHandler: Throwable => F[Response[F]] = self.errorHandler,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit] = self.onWriteFailure,
      maxConnections: Int = self.maxConnections,
      receiveBufferSize: Int = self.receiveBufferSize,
      maxHeaderSize: Int = self.maxHeaderSize,
      requestHeaderReceiveTimeout: Duration = self.requestHeaderReceiveTimeout,
      idleTimeout: Duration = self.idleTimeout,
      shutdownTimeout: Duration = self.shutdownTimeout,
      additionalSocketOptions: List[SocketOptionMapping[_]] = self.additionalSocketOptions,
      logger: Logger[F] = self.logger,
  ): EmberServerBuilder[F] =
    new EmberServerBuilder[F](
      host = host,
      port = port,
      httpApp = httpApp,
      blockerOpt = blockerOpt,
      tlsInfoOpt = tlsInfoOpt,
      sgOpt = sgOpt,
      errorHandler = errorHandler,
      onWriteFailure = onWriteFailure,
      maxConnections = maxConnections,
      receiveBufferSize = receiveBufferSize,
      maxHeaderSize = maxHeaderSize,
      requestHeaderReceiveTimeout = requestHeaderReceiveTimeout,
      idleTimeout = idleTimeout,
      shutdownTimeout = shutdownTimeout,
      additionalSocketOptions = additionalSocketOptions,
      logger = logger,
    )

  def withHost(host: String): EmberServerBuilder[F] = copy(host = host)
  def withPort(port: Int): EmberServerBuilder[F] = copy(port = port)
  def withHttpApp(httpApp: HttpApp[F]): EmberServerBuilder[F] = copy(httpApp = httpApp)

  def withSocketGroup(sg: SocketGroup): EmberServerBuilder[F] =
    copy(sgOpt = sg.pure[Option])

  def withTLS(tlsContext: TLSContext, tlsParameters: TLSParameters = TLSParameters.Default): EmberServerBuilder[F] =
    copy(tlsInfoOpt = (tlsContext, tlsParameters).pure[Option])
  def withoutTLS: EmberServerBuilder[F] =
    copy(tlsInfoOpt = None)

  def withBlocker(blocker: Blocker): EmberServerBuilder[F] =
    copy(blockerOpt = blocker.pure[Option])

  def withIdleTimeout(idleTimeout: Duration): EmberServerBuilder[F] =
    copy(idleTimeout = idleTimeout)

  def withShutdownTimeout(shutdownTimeout: Duration): EmberServerBuilder[F] =
    copy(shutdownTimeout = shutdownTimeout)

  @deprecated("0.21.17", "Use withErrorHandler - Do not allow the F to fail")
  def withOnError(onError: Throwable => Response[F]): EmberServerBuilder[F] =
    withErrorHandler { case e => onError(e).pure[F] }

  def withErrorHandler(errorHandler: PartialFunction[Throwable, F[Response[F]]]): EmberServerBuilder[F] =
    copy(errorHandler = errorHandler)

  def withOnWriteFailure(onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit]): EmberServerBuilder[F] =
    copy(onWriteFailure = onWriteFailure)

  @deprecated("Use org.http4s.ember.server.EmberServerBuilder.withMaxConnections", "0.22.3")
  def withMaxConcurrency(maxConcurrency: Int): EmberServerBuilder[F] = copy(maxConnections = maxConcurrency)

  def withMaxConnections(maxConnections: Int): EmberServerBuilder[F] = copy(maxConnections = maxConnections)

  def withReceiveBufferSize(receiveBufferSize: Int): EmberServerBuilder[F] = copy(receiveBufferSize = receiveBufferSize)
  def withMaxHeaderSize(maxHeaderSize: Int): EmberServerBuilder[F] = copy(maxHeaderSize = maxHeaderSize)
  def withRequestHeaderReceiveTimeout(requestHeaderReceiveTimeout: Duration): EmberServerBuilder[F] =
    copy(requestHeaderReceiveTimeout = requestHeaderReceiveTimeout)
  def withLogger(l: Logger[F]): EmberServerBuilder[F] = copy(logger = l)

  def build: Resource[F, Server] =
    for {
      bindAddress <- Resource.eval(Sync[F].delay(new InetSocketAddress(host, port)))
      blocker <- blockerOpt.fold(Blocker[F])(_.pure[Resource[F, *]])
      sg <- sgOpt.fold(SocketGroup[F](blocker))(_.pure[Resource[F, *]])
      ready <- Resource.eval(Deferred[F, Either[Throwable, Unit]])
      shutdown <- Resource.eval(Shutdown[F](shutdownTimeout))
      _ <- Concurrent[F].background(
        ServerHelpers
          .server(
            bindAddress,
            httpApp,
            sg,
            tlsInfoOpt,
            ready,
            shutdown,
            errorHandler,
            onWriteFailure,
            maxConnections,
            receiveBufferSize,
            maxHeaderSize,
            requestHeaderReceiveTimeout,
            idleTimeout,
            additionalSocketOptions,
            logger,
          )
          .compile
          .drain
      )
      _ <- Resource.make(Applicative[F].unit)(_ => shutdown.await)
      _ <- Resource.eval(ready.get.rethrow)
      _ <- Resource.eval(logger.info(s"Ember-Server service bound to address: $bindAddress"))
    } yield new Server {
      def address: InetSocketAddress = bindAddress
      def isSecure: Boolean = tlsInfoOpt.isDefined
    }
}

object EmberServerBuilder {
  def default[F[_]: Concurrent: Timer: ContextShift]: EmberServerBuilder[F] =
    new EmberServerBuilder[F](
      host = Defaults.host,
      port = Defaults.port,
      httpApp = Defaults.httpApp[F],
      blockerOpt = None,
      tlsInfoOpt = None,
      sgOpt = None,
      errorHandler = Defaults.errorHandler[F],
      onWriteFailure = Defaults.onWriteFailure[F],
      maxConnections = Defaults.maxConnections,
      receiveBufferSize = Defaults.receiveBufferSize,
      maxHeaderSize = Defaults.maxHeaderSize,
      requestHeaderReceiveTimeout = Defaults.requestHeaderReceiveTimeout,
      idleTimeout = Defaults.idleTimeout,
      shutdownTimeout = Defaults.shutdownTimeout,
      additionalSocketOptions = Defaults.additionalSocketOptions,
      logger = Slf4jLogger.getLogger[F],
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
    val maxConnections: Int = server.defaults.MaxConnections
    val receiveBufferSize: Int = 256 * 1024
    val maxHeaderSize: Int = server.defaults.MaxHeadersSize
    val requestHeaderReceiveTimeout: Duration = 5.seconds
    val idleTimeout: Duration = server.defaults.IdleTimeout
    val shutdownTimeout: Duration = server.defaults.ShutdownTimeout
    val additionalSocketOptions = List.empty[SocketOptionMapping[_]]
  }
}
