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
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.io.net.Network
import fs2.io.net.SocketGroup
import fs2.io.net.SocketOption
import fs2.io.net.tls._
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import org.http4s._
import org.http4s.ember.core.EmberException
import org.http4s.ember.server.internal.ServerHelpers
import org.http4s.ember.server.internal.Shutdown
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen

import scala.concurrent.duration._

final class EmberServerBuilder[F[_]: Async: Network] private (
    val host: Option[Host],
    val port: Port,
    private val httpApp: WebSocketBuilder[F] => HttpApp[F],
    private val tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
    private val sgOpt: Option[SocketGroup[F]],
    private val connectionErrorHandler: PartialFunction[Throwable, F[Unit]],
    private val errorHandler: Throwable => F[Response[F]],
    private val onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
    val maxConnections: Int,
    val receiveBufferSize: Int,
    val maxHeaderSize: Int,
    val requestHeaderReceiveTimeout: Duration,
    val idleTimeout: Duration,
    val shutdownTimeout: Duration,
    val additionalSocketOptions: List[SocketOption],
    private val logger: Logger[F],
    private val unixSocketConfig: Option[(UnixSockets[F], UnixSocketAddress, Boolean, Boolean)],
    private val enableHttp2: Boolean,
    private val requestLineParseErrorHandler: Throwable => F[Response[F]],
    private val maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]],
) { self =>

  @deprecated("Use org.http4s.ember.server.EmberServerBuilder.maxConnections", "0.22.3")
  val maxConcurrency: Int = maxConnections

  private def copy(
      host: Option[Host] = self.host,
      port: Port = self.port,
      httpApp: WebSocketBuilder[F] => HttpApp[F] = self.httpApp,
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)] = self.tlsInfoOpt,
      sgOpt: Option[SocketGroup[F]] = self.sgOpt,
      connectionErrorHandler: PartialFunction[Throwable, F[Unit]] = self.connectionErrorHandler,
      errorHandler: Throwable => F[Response[F]] = self.errorHandler,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit] = self.onWriteFailure,
      maxConnections: Int = self.maxConnections,
      receiveBufferSize: Int = self.receiveBufferSize,
      maxHeaderSize: Int = self.maxHeaderSize,
      requestHeaderReceiveTimeout: Duration = self.requestHeaderReceiveTimeout,
      idleTimeout: Duration = self.idleTimeout,
      shutdownTimeout: Duration = self.shutdownTimeout,
      additionalSocketOptions: List[SocketOption] = self.additionalSocketOptions,
      logger: Logger[F] = self.logger,
      unixSocketConfig: Option[(UnixSockets[F], UnixSocketAddress, Boolean, Boolean)] =
        self.unixSocketConfig,
      enableHttp2: Boolean = self.enableHttp2,
      requestLineParseErrorHandler: Throwable => F[Response[F]] = self.requestLineParseErrorHandler,
      maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]] =
        self.maxHeaderSizeErrorHandler,
  ): EmberServerBuilder[F] =
    new EmberServerBuilder[F](
      host = host,
      port = port,
      httpApp = httpApp,
      tlsInfoOpt = tlsInfoOpt,
      sgOpt = sgOpt,
      connectionErrorHandler = connectionErrorHandler,
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
      unixSocketConfig = unixSocketConfig,
      enableHttp2 = enableHttp2,
      requestLineParseErrorHandler = requestLineParseErrorHandler,
      maxHeaderSizeErrorHandler = maxHeaderSizeErrorHandler,
    )

  def withHostOption(host: Option[Host]): EmberServerBuilder[F] = copy(host = host)
  def withHost(host: Host): EmberServerBuilder[F] = withHostOption(Some(host))
  def withoutHost: EmberServerBuilder[F] = withHostOption(None)

  def withPort(port: Port): EmberServerBuilder[F] = copy(port = port)
  def withHttpApp(httpApp: HttpApp[F]): EmberServerBuilder[F] = copy(httpApp = _ => httpApp)
  def withHttpWebSocketApp(f: WebSocketBuilder[F] => HttpApp[F]): EmberServerBuilder[F] =
    copy(httpApp = f)

  def withSocketGroup(sg: SocketGroup[F]): EmberServerBuilder[F] =
    copy(sgOpt = sg.pure[Option])

  def withTLS(
      tlsContext: TLSContext[F],
      tlsParameters: TLSParameters = TLSParameters.Default,
  ): EmberServerBuilder[F] =
    copy(tlsInfoOpt = (tlsContext, tlsParameters).pure[Option])
  def withoutTLS: EmberServerBuilder[F] =
    copy(tlsInfoOpt = None)

  def withIdleTimeout(idleTimeout: Duration): EmberServerBuilder[F] =
    copy(idleTimeout = idleTimeout)

  def withShutdownTimeout(shutdownTimeout: Duration): EmberServerBuilder[F] =
    copy(shutdownTimeout = shutdownTimeout)

  /** Called when an error occurs while attempting to read a connection.
    *
    * For example on JVM `javax.net.ssl.SSLException` may be thrown if the client doesn't speak SSL.
    *
    * If the [[scala.PartialFunction]] does not match the error is just logged.
    */
  def withConnectionErrorHandler(
      errorHandler: PartialFunction[Throwable, F[Unit]]
  ): EmberServerBuilder[F] =
    copy(connectionErrorHandler = errorHandler)

  @deprecated("Use withErrorHandler - Do not allow the F to fail", "0.21.17")
  def withOnError(onError: Throwable => Response[F]) =
    withErrorHandler { case e => onError(e).pure[F] }

  def withErrorHandler(errorHandler: PartialFunction[Throwable, F[Response[F]]]) =
    copy(errorHandler = errorHandler)

  def withOnWriteFailure(onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit]) =
    copy(onWriteFailure = onWriteFailure)

  @deprecated("Use org.http4s.ember.server.EmberServerBuilder.withMaxConnections", "0.22.3")
  def withMaxConcurrency(maxConcurrency: Int): EmberServerBuilder[F] =
    copy(maxConnections = maxConcurrency)

  def withMaxConnections(maxConnections: Int): EmberServerBuilder[F] =
    copy(maxConnections = maxConnections)

  def withReceiveBufferSize(receiveBufferSize: Int): EmberServerBuilder[F] =
    copy(receiveBufferSize = receiveBufferSize)

  def withMaxHeaderSize(maxHeaderSize: Int): EmberServerBuilder[F] =
    copy(maxHeaderSize = maxHeaderSize)

  /** Customizes the error response when the request's header fields
    * exceed `maxHeaderSize`.  The default behavior is to return an
    * `431 Request Header Fields Too Large` response.
    *
    * @see [[https://www.rfc-editor.org/rfc/rfc9110.html#name-field-limits RFC 9110, Section 5.4]]
    * @see [[https://www.rfc-editor.org/rfc/rfc6585.html#section-5 RFC 6585, Section 5]]
    */
  def withMaxHeaderSizeErrorHandler(
      maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]]
  ): EmberServerBuilder[F] =
    copy(maxHeaderSizeErrorHandler = maxHeaderSizeErrorHandler)

  def withRequestHeaderReceiveTimeout(
      requestHeaderReceiveTimeout: Duration
  ): EmberServerBuilder[F] =
    copy(requestHeaderReceiveTimeout = requestHeaderReceiveTimeout)
  def withLogger(l: Logger[F]): EmberServerBuilder[F] = copy(logger = l)

  def withHttp2: EmberServerBuilder[F] = copy(enableHttp2 = true)
  def withoutHttp2: EmberServerBuilder[F] = copy(enableHttp2 = false)

  // If used will bind to UnixSocket
  def withUnixSocketConfig(
      unixSockets: UnixSockets[F],
      unixSocketAddress: UnixSocketAddress,
      deleteIfExists: Boolean = true,
      deleteOnClose: Boolean = true,
  ): EmberServerBuilder[F] =
    copy(unixSocketConfig = Some((unixSockets, unixSocketAddress, deleteIfExists, deleteOnClose)))
  def withoutUnixSocketConfig: EmberServerBuilder[F] =
    copy(unixSocketConfig = None)

  def withAdditionalSocketOptions(
      additionalSocketOptions: List[SocketOption]
  ): EmberServerBuilder[F] =
    copy(additionalSocketOptions = additionalSocketOptions)

  /** An error handler which will run in cases where the server is unable to
    * parse the "start-line" (http 2 name) or "request-line" (http 1.1
    * name). This is the first line of the request, e.g. "GET / HTTP/1.1".
    *
    * In this case, RFC 9112 (HTTP 2) says a 400 should be returned.
    *
    * This handler allows for configuring the behavior. The default as of
    * 0.23.19 is to return a 400.
    *
    * @see [[https://www.rfc-editor.org/rfc/rfc9112#section-2.2-9 RFC 9112]]
    * @see [[https://www.rfc-editor.org/rfc/rfc7230 RFC 7230]]
    */
  def withRequestLineParseErrorHandler(
      requestLineParseErrorHandler: Throwable => F[Response[F]]
  ): EmberServerBuilder[F] =
    copy(requestLineParseErrorHandler = requestLineParseErrorHandler)

  def build: Resource[F, Server] =
    for {
      sg <- sgOpt.getOrElse(Network[F]).pure[Resource[F, *]]
      ready <- Resource.eval(Deferred[F, Either[Throwable, SocketAddress[IpAddress]]])
      shutdown <- Resource.eval(Shutdown[F](shutdownTimeout))
      wsBuilder <- Resource.eval(WebSocketBuilder[F])
      _ <- unixSocketConfig.fold(
        Concurrent[F].background(
          ServerHelpers
            .server(
              host,
              port,
              additionalSocketOptions,
              sg,
              httpApp(wsBuilder),
              tlsInfoOpt,
              ready,
              shutdown,
              connectionErrorHandler,
              errorHandler,
              onWriteFailure,
              maxConnections,
              receiveBufferSize,
              maxHeaderSize,
              requestHeaderReceiveTimeout,
              idleTimeout,
              logger,
              wsBuilder.webSocketKey,
              enableHttp2,
              requestLineParseErrorHandler,
              maxHeaderSizeErrorHandler,
            )
            .compile
            .drain
        )
      ) { case (unixSockets, unixSocketAddress, deleteIfExists, deleteOnClose) =>
        ServerHelpers
          .unixSocketServer(
            unixSockets,
            unixSocketAddress,
            deleteIfExists,
            deleteOnClose,
            httpApp(wsBuilder),
            tlsInfoOpt,
            ready,
            shutdown,
            connectionErrorHandler,
            errorHandler,
            onWriteFailure,
            maxConnections,
            receiveBufferSize,
            maxHeaderSize,
            requestHeaderReceiveTimeout,
            idleTimeout,
            logger,
            wsBuilder.webSocketKey,
            enableHttp2,
            requestLineParseErrorHandler,
            maxHeaderSizeErrorHandler,
          )
          .compile
          .drain
          .background
      }
      _ <- Resource.onFinalize(shutdown.await)
      bindAddress <- Resource.eval(ready.get.rethrow)
      _ <- Resource.eval(logger.info(s"Ember-Server service bound to address: ${bindAddress}"))
    } yield new Server {
      def address: SocketAddress[IpAddress] = bindAddress
      def isSecure: Boolean = tlsInfoOpt.isDefined
    }
}

object EmberServerBuilder {
  def default[F[_]: Async: Network: LoggerFactoryGen]: EmberServerBuilder[F] =
    new EmberServerBuilder[F](
      host = Host.fromString(Defaults.host),
      port = Port.fromInt(Defaults.port).get,
      httpApp = _ => Defaults.httpApp[F],
      tlsInfoOpt = None,
      sgOpt = None,
      connectionErrorHandler = Defaults.connectionErrorHandler[F],
      errorHandler = Defaults.errorHandler[F],
      onWriteFailure = Defaults.onWriteFailure[F],
      maxConnections = Defaults.maxConnections,
      receiveBufferSize = Defaults.receiveBufferSize,
      maxHeaderSize = Defaults.maxHeaderSize,
      requestHeaderReceiveTimeout = Defaults.requestHeaderReceiveTimeout,
      idleTimeout = Defaults.idleTimeout,
      shutdownTimeout = Defaults.shutdownTimeout,
      additionalSocketOptions = Defaults.additionalSocketOptions,
      logger = LoggerFactory.getLogger[F],
      unixSocketConfig = None,
      enableHttp2 = false,
      requestLineParseErrorHandler = Defaults.requestLineParseErrorHandler,
      maxHeaderSizeErrorHandler = Defaults.maxHeaderSizeErrorHandler,
    )

  private object Defaults {
    val host: String = server.defaults.IPv4Host
    val port: Int = server.defaults.HttpPort

    def httpApp[F[_]: Applicative]: HttpApp[F] = HttpApp.notFound[F]

    private val serverFailure =
      Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)

    def connectionErrorHandler[F[_]]: PartialFunction[Throwable, F[Unit]] =
      PartialFunction.empty[Throwable, F[Unit]]

    // Effectful Handler - Perhaps a Logger
    // Will only arrive at this code if your HttpApp fails or the request receiving fails for some reason
    def errorHandler[F[_]](implicit F: Applicative[F]): Throwable => F[Response[F]] = {
      case (_: Throwable) =>
        F.pure(serverFailure)
    }

    @deprecated("Use errorHandler, default fallback of failure InternalServerFailure", "0.21.17")
    def onError[F[_]]: Throwable => Response[F] = { (_: Throwable) =>
      serverFailure
    }

    def requestLineParseErrorHandler[F[_]: Applicative]: Throwable => F[Response[F]] = { case _ =>
      Response[F](
        Status.BadRequest,
        HttpVersion.`HTTP/1.1`,
        Headers(org.http4s.headers.`Content-Length`.zero),
      ).pure[F]
    }

    def maxHeaderSizeErrorHandler[F[_]: Applicative]
        : EmberException.MessageTooLong => F[Response[F]] = { case _ =>
      Response[F](
        Status.RequestHeaderFieldsTooLarge,
        HttpVersion.`HTTP/1.1`,
        Headers(org.http4s.headers.`Content-Length`.zero),
      ).pure[F]
    }

    def onWriteFailure[F[_]: Applicative]
        : (Option[Request[F]], Response[F], Throwable) => F[Unit] = {
      case _: (Option[Request[F]], Response[F], Throwable) => Applicative[F].unit
    }
    val maxConnections: Int = server.defaults.MaxConnections
    val receiveBufferSize: Int = 256 * 1024
    val maxHeaderSize: Int = server.defaults.MaxHeadersSize
    val requestHeaderReceiveTimeout: Duration = 5.seconds
    val idleTimeout: Duration = org.http4s.ember.core.Defaults.IdleTimeout
    val shutdownTimeout: Duration = server.defaults.ShutdownTimeout
    val additionalSocketOptions: List[SocketOption] = Nil
  }
}
