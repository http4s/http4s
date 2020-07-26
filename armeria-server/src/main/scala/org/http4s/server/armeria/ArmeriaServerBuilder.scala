/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package armeria

import cats.effect.{ConcurrentEffect, Resource}
import com.linecorp.armeria.common.util.Version
import com.linecorp.armeria.common.{HttpRequest, HttpResponse, SessionProtocol}
import com.linecorp.armeria.server.{HttpService, ServerListenerAdapter, ServiceRequestContext, Server => ArmeriaServer, ServerBuilder => ArmeriaBuilder}
import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import java.io.{File, InputStream}
import java.net.InetSocketAddress
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.http4s.server._
import org.http4s.syntax.all._
import java.util.function.{Function => JFunction}
import javax.net.ssl.KeyManagerFactory
import org.http4s.server.defaults.{IdleTimeout, ResponseTimeout, ShutdownTimeout}
import org.log4s.{Logger, getLogger}
import scala.concurrent.duration.Duration

sealed class ArmeriaServerBuilder[F[_]] private (
  armeriaServerBuilder: ArmeriaBuilder,
  socketAddress: InetSocketAddress,
  serviceErrorHandler: ServiceErrorHandler[F],
  banner: Seq[String]
)(implicit protected val F: ConcurrentEffect[F]) extends ServerBuilder[F] {
  override type Self = ArmeriaServerBuilder[F]

  private[this] val logger: Logger = getLogger

  type DecoratorFunction = (HttpService, ServiceRequestContext, HttpRequest) => HttpResponse

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  override def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  override def resource: Resource[F, Server] =
    Resource(F.delay {
      val armeriaServer = armeriaServerBuilder
        .http(socketAddress)
        .build()

      armeriaServer.addListener(new ServerListenerAdapter {
        override def serverStarting(server: ArmeriaServer): Unit = {
          banner.foreach(logger.info(_))

          val armeriaVersion = Version.get("armeria").artifactVersion()

          logger.info(s"http4s v${BuildInfo.version} on Armeria v${armeriaVersion} started")
        }
      })
      armeriaServer.start().join()

      val server = new Server {
        lazy val address: InetSocketAddress = {
          val host = socketAddress.getHostString
          val port = armeriaServer.activeLocalPort()
          new InetSocketAddress(host, port)
        }

        override def isSecure: Boolean = armeriaServer.activePort(SessionProtocol.HTTPS) != null
      }

      server -> shutdown(armeriaServer)
    })

  /** Binds the specified `service` at the specified path pattern.
    * See [[https://armeria.dev/docs/server-basics#path-patterns]] for detailed information of path pattens.
    */
  def withHttpService(
    pathPattern: String,
    service: (ServiceRequestContext, HttpRequest) => HttpResponse): Self = {
    armeriaServerBuilder.service(
      pathPattern,
      new HttpService {
        override def serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse =
          service(ctx, req)
      })
    this
  }

  /** Binds the specified [[HttpService]] at the specified path pattern.
    * See [[https://armeria.dev/docs/server-basics#path-patterns]] for detailed information of path pattens.
    */
  def withHttpService(pathPattern: String, service: HttpService): Self = {
    armeriaServerBuilder.service(pathPattern, service)
    this
  }

  /** Binds the specified [[HttpRoutes]] under the specified prefix. */
  def withHttpRoutes(prefix: String, service: HttpRoutes[F]): Self =
    withHttpApp(prefix, service.orNotFound)

  /** Binds the specified [[HttpApp]] under the specified prefix. */
  def withHttpApp(prefix: String, service: HttpApp[F]): Self = {
    armeriaServerBuilder.serviceUnder(prefix, ArmeriaHttp4sHandler(prefix, service))
    this
  }

  /** Decorates all HTTP services with the specified [[DecoratorFunction]]. */
  def withDecorator(decorator: DecoratorFunction): Self = {
    armeriaServerBuilder.decorator((delegate, ctx, req) => decorator(delegate, ctx, req))
    this
  }

  /** Decorates all HTTP services with the specified `decorator`. */
  def withDecorator(decorator: JFunction[_ >: HttpService, _ <: HttpService]): Self = {
    armeriaServerBuilder.decorator(decorator)
    this
  }

  /** Decorates HTTP services under the specified directory with the specified [[DecoratorFunction]]. */
  def withDecoratorUnder(prefix: String, decorator: DecoratorFunction): Self = {
    armeriaServerBuilder.decoratorUnder(prefix, (delegate, ctx, req) => decorator(delegate, ctx, req))
    this
  }

  /** Decorates HTTP services under the specified directory with the specified `decorator`. */
  def withDecoratorUnder(
    prefix: String,
    decorator: JFunction[_ >: HttpService, _ <: HttpService]): Self = {
    armeriaServerBuilder.decoratorUnder(prefix, decorator)
    this
  }

  /** Configures the Armeria server using the specified [[ArmeriaBuilder]]. */
  def withArmeriaBuilder(customizer: ArmeriaBuilder => Unit): Self = {
    customizer(armeriaServerBuilder)
    this
  }

  /** Sets the idle timeout of a connection in milliseconds for keep-alive.
    *
    * @param idleTimeout the timeout. [[Duration.Zero]] disables the timeout.
    */
  def withIdleTimeout(idleTimeout: Duration): Self = {
    armeriaServerBuilder.idleTimeoutMillis(idleTimeout.toMillis)
    this
  }

  /** Sets the timeout of a request.
    *
    * @param requestTimeout the timeout. [[Duration.Zero]] disables the timeout.
    */
  def withRequestTimeout(requestTimeout: Duration): Self = {
    armeriaServerBuilder.requestTimeoutMillis(requestTimeout.toMillis)
    this
  }

  /** Adds an HTTP port that listens on all available network interfaces.
    *
    * @param port the HTTP port number.
    * @see [[ArmeriaBuilder#http(java.net.InetSocketAddress)]]
    */
  def withHttp(port: Int): Self = {
    armeriaServerBuilder.http(port)
    this
  }

  /** Adds an HTTPS port that listens on all available network interfaces.
    *
    * @param port the HTTPS port number.
    * @see [[ArmeriaBuilder#https(java.net.InetSocketAddress)]]
    */
  def withHttps(port: Int): Self = {
    armeriaServerBuilder.https(port)
    this
  }

  /** Sets the [[ChannelOption]] of the server socket bound by [[ArmeriaServer]].
    * Note that the previously added option will be overridden if the same option is set again.
    *
    * @see [[https://armeria.dev/docs/advanced-production-checklist Production checklist]]
    */
  def withChannelOption[T](option: ChannelOption[T], value: T): Self = {
    armeriaServerBuilder.channelOption(option, value)
    this
  }

  /** Sets the [[ChannelOption]] of sockets accepted by [[ArmeriaServer]].
    * Note that the previously added option will be overridden if the same option is set again.
    *
    * @see [[https://armeria.dev/docs/advanced-production-checklist Production checklist]]
    */
  def withChildChannelOption[T](option: ChannelOption[T], value: T): Self = {
    armeriaServerBuilder.childChannelOption(option, value)
    this
  }

  def withTls(keyCertChainFile: File, keyFile: File, keyPassword: Option[String]): Self = {
    armeriaServerBuilder.tls(keyCertChainFile, keyFile, keyPassword.orNull)
    this
  }

  /** Configures SSL or TLS of this [[ArmeriaServer]] with the specified `keyCertChainInputStream`,
    * `keyInputStream` and `keyPassword`.
    *
    * @see [[withTlsCustomizer(scala.Function1)]]
    */
  def withTls(keyCertChainInputStream: InputStream, keyInputStream: InputStream, keyPassword: Option[String])
  : Self = {
    armeriaServerBuilder.tls(keyCertChainInputStream, keyInputStream, keyPassword.orNull)
    this
  }

  /** Configures SSL or TLS of this [[ArmeriaServer]] with the specified cleartext [[PrivateKey]] and
    * [[X509Certificate]] chain.
    *
    * @see [[withTlsCustomizer(scala.Function1)]]
    */
  def withTls(key: PrivateKey, keyCertChain: X509Certificate*): Self = {
    armeriaServerBuilder.tls(key, keyCertChain: _*)
    this
  }

  /** Configures SSL or TLS of this [[ArmeriaServer]] with the specified [[KeyManagerFactory]].
    *
    * @see [[withTlsCustomizer(scala.Function1)]]
    */
  def withTls(keyManagerFactory: KeyManagerFactory): Self = {
    armeriaServerBuilder.tls(keyManagerFactory)
    this
  }

  /** Configures SSL or TLS of the [[ArmeriaServer]] with an auto-generated self-signed certificate.
    * <strong>Note:</strong> You should never use this in production but only for a testing purpose.
    *
    * @see [[withTlsCustomizer(scala.Function1)]]
    */
  def withTlsSelfSigned: Self = {
    armeriaServerBuilder.tlsSelfSigned
    this
  }

  /** Adds the specified `tlsCustomizer` which can arbitrarily configure the [[SslContextBuilder]] that will be
    * applied to the SSL session.
    */
  def withTlsCustomizer(tlsCustomizer: SslContextBuilder => Unit): Self = {
    armeriaServerBuilder.tlsCustomizer(ctxBuilder => tlsCustomizer(ctxBuilder))
    this
  }

  /** Sets the [[MeterRegistry]] that collects various stats. */
  def meterRegistry(meterRegistry: MeterRegistry): Self = {
    armeriaServerBuilder.meterRegistry(meterRegistry)
    this
  }

  private def shutdown(armeriaServer: ArmeriaServer) =
    F.async[Unit] { cb =>
      val _ = armeriaServer
        .stop()
        .whenComplete { (_, cause) =>
          if (cause == null)
            cb(Right(()))
          else
            cb(Left(cause))
        }
    }

  override def withBanner(banner: Seq[String]): Self = copy(banner = banner)

  private def copy(
      armeriaServerBuilder: ArmeriaBuilder = armeriaServerBuilder,
      socketAddress: InetSocketAddress = socketAddress,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: Seq[String] = banner
  ): Self =
    new ArmeriaServerBuilder(armeriaServerBuilder, socketAddress, serviceErrorHandler, banner)
}

object ArmeriaServerBuilder {
  def apply[F[_] : ConcurrentEffect]: ArmeriaServerBuilder[F] = {
    val defaultServerBuilder =
      ArmeriaServer.builder()
                   .idleTimeoutMillis(IdleTimeout.toMillis)
                   .requestTimeoutMillis(ResponseTimeout.toMillis)
                   .gracefulShutdownTimeoutMillis(ShutdownTimeout.toMillis, ShutdownTimeout.toMillis)
    new ArmeriaServerBuilder(
      armeriaServerBuilder = defaultServerBuilder,
      socketAddress = defaults.SocketAddress,
      serviceErrorHandler = DefaultServiceErrorHandler,
      banner = defaults.Banner)
  }
}
