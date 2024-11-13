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
package jetty
package server

import cats.effect._
import cats.syntax.all._
import org.eclipse.jetty.ee8.servlet.FilterHolder
import org.eclipse.jetty.ee8.servlet.ServletContextHandler
import org.eclipse.jetty.ee8.servlet.ServletHolder
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.{Server => JServer}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.ThreadPool
import org.http4s.jetty.server.JettyBuilder._
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.server.SSLClientAuthMode
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server
import org.http4s.server.ServerBuilder
import org.http4s.server.ServiceErrorHandler
import org.http4s.server.defaults
import org.http4s.servlet.AsyncHttp4sServlet
import org.http4s.servlet.ServletContainer
import org.http4s.servlet.ServletIo
import org.http4s.syntax.all._
import org.log4s.getLogger

import java.net.InetSocketAddress
import java.util
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.servlet.DispatcherType
import javax.servlet.Filter
import javax.servlet.http.HttpServlet
import scala.collection.immutable
import scala.concurrent.duration._

sealed class JettyBuilder[F[_]] private (
    socketAddress: InetSocketAddress,
    private val threadPool: ThreadPool,
    threadPoolResourceOption: Option[Resource[F, ThreadPool]],
    private val idleTimeout: Duration,
    private val asyncTimeout: Duration,
    shutdownTimeout: Duration,
    private val servletIo: ServletIo[F],
    sslConfig: SslConfig,
    mounts: Vector[Mount[F]],
    private val serviceErrorHandler: ServiceErrorHandler[F],
    supportHttp2: Boolean,
    banner: immutable.Seq[String],
    jettyHttpConfiguration: HttpConfiguration,
)(implicit protected val F: ConcurrentEffect[F])
    extends ServletContainer[F]
    with ServerBuilder[F] {
  type Self = JettyBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      threadPool: ThreadPool = threadPool,
      threadPoolResourceOption: Option[Resource[F, ThreadPool]] = threadPoolResourceOption,
      idleTimeout: Duration = idleTimeout,
      asyncTimeout: Duration = asyncTimeout,
      shutdownTimeout: Duration = shutdownTimeout,
      servletIo: ServletIo[F] = servletIo,
      sslConfig: SslConfig = sslConfig,
      mounts: Vector[Mount[F]] = mounts,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      supportHttp2: Boolean = supportHttp2,
      banner: immutable.Seq[String] = banner,
      jettyHttpConfiguration: HttpConfiguration = jettyHttpConfiguration,
  ): Self =
    new JettyBuilder(
      socketAddress,
      threadPool,
      threadPoolResourceOption,
      idleTimeout,
      asyncTimeout,
      shutdownTimeout,
      servletIo,
      sslConfig,
      mounts,
      serviceErrorHandler,
      supportHttp2,
      banner,
      jettyHttpConfiguration,
    )

  @deprecated(
    "Build an `SSLContext` from the first four parameters and use `withSslContext` (note lowercase). To also request client certificates, use `withSslContextAndParameters, calling either `.setWantClientAuth(true)` or `setNeedClientAuth(true)` on the `SSLParameters`.",
    "0.21.0-RC3",
  )
  def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String = "TLS",
      trustStore: Option[StoreInfo] = None,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested,
  ): Self =
    copy(sslConfig =
      new KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)
    )

  @deprecated(
    "Use `withSslContext` (note lowercase). To request client certificates, use `withSslContextAndParameters, calling either `.setWantClientAuth(true)` or `setNeedClientAuth(true)` on the `SSLParameters`.",
    "0.21.0-RC3",
  )
  def withSSLContext(
      sslContext: SSLContext,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested,
  ): Self =
    copy(sslConfig = new ContextWithClientAuth(sslContext, clientAuth))

  /** Configures the server with TLS, using the provided `SSLContext` and its
    * default `SSLParameters`
    */
  def withSslContext(sslContext: SSLContext): Self =
    copy(sslConfig = new ContextOnly(sslContext))

  /** Configures the server with TLS, using the provided `SSLContext` and its
    * default `SSLParameters`
    */
  def withSslContextAndParameters(sslContext: SSLContext, sslParameters: SSLParameters): Self =
    copy(sslConfig = new ContextWithParameters(sslContext, sslParameters))

  /** Disables SSL. */
  def withoutSsl: Self =
    copy(sslConfig = NoSsl)

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  /** Set the [[org.eclipse.jetty.util.thread.ThreadPool]] that Jetty will use.
    *
    * It is recommended you use [[JettyThreadPools#resource]] to build this so
    * that it will be gracefully shutdown when/if the Jetty server is
    * shutdown.
    */
  def withThreadPoolResource(threadPoolResource: Resource[F, ThreadPool]): JettyBuilder[F] =
    copy(threadPoolResourceOption = Some(threadPoolResource))

  /** Set the [[org.eclipse.jetty.util.thread.ThreadPool]] that Jetty will use.
    *
    * @note You should prefer [[withThreadPoolResource]] instead of this
    *       method. If you invoke this method the provided
    *       [[org.eclipse.jetty.util.thread.ThreadPool]] ''will not'' be
    *       joined, stopped, or destroyed when/if the Jetty server stops. This
    *       is to preserve the <= 0.21.23 semantics.
    */
  @deprecated(
    message = "Please use withThreadPoolResource instead and see JettyThreadPools.",
    since = "0.21.23",
  )
  def withThreadPool(threadPool: ThreadPool): JettyBuilder[F] =
    copy(threadPoolResourceOption = None, threadPool = new UndestroyableThreadPool(threadPool))

  override def mountServlet(
      servlet: HttpServlet,
      urlMapping: String,
      name: Option[String] = None,
  ): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, _) =>
      val servletName = name.getOrElse(s"servlet-$index")
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  override def mountFilter(
      filter: Filter,
      urlMapping: String,
      name: Option[String],
      dispatches: util.EnumSet[DispatcherType],
  ): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, _) =>
      val filterName = name.getOrElse(s"filter-$index")
      val filterHolder = new FilterHolder(filter)
      filterHolder.setName(filterName)
      context.addFilter(filterHolder, urlMapping, dispatches)
    })

  def mountService(service: HttpRoutes[F], prefix: String): Self =
    mountHttpApp(service.orNotFound, prefix)

  def mountHttpApp(service: HttpApp[F], prefix: String): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, builder) =>
      val servlet = new AsyncHttp4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
        serviceErrorHandler = builder.serviceErrorHandler,
      )
      val servletName = s"servlet-$index"
      val urlMapping = ServletContainer.prefixMapping(prefix)
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  def withIdleTimeout(idleTimeout: Duration): Self =
    copy(idleTimeout = idleTimeout)

  def withAsyncTimeout(asyncTimeout: Duration): Self =
    copy(asyncTimeout = asyncTimeout)

  /** Sets the graceful shutdown timeout for Jetty.  Closing the resource
    * will wait this long before a forcible stop.
    */
  def withShutdownTimeout(shutdownTimeout: Duration): Self =
    copy(shutdownTimeout = shutdownTimeout)

  override def withServletIo(servletIo: ServletIo[F]): Self =
    copy(servletIo = servletIo)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  /** Enables HTTP/2 connection upgrade over plain text (no TLS).
    * See https://webtide.com/introduction-to-http2-in-jetty
    */
  def withHttp2c: Self =
    copy(supportHttp2 = true)

  def withoutHttp2c: Self =
    copy(supportHttp2 = false)

  def withBanner(banner: immutable.Seq[String]): Self =
    copy(banner = banner)

  /** Provide a specific [[org.eclipse.jetty.server.HttpConfiguration]].
    *
    * This can be used for direct low level control over many HTTP related
    * configuration settings which http4s may not directly expose.
    */
  def withJettyHttpConfiguration(value: HttpConfiguration): Self =
    copy(jettyHttpConfiguration = value)

  private def getConnector(jetty: JServer): ServerConnector = {

    val http1: HttpConnectionFactory = new HttpConnectionFactory(jettyHttpConfiguration)

    sslConfig.makeSslContextFactory match {
      case Some(sslContextFactory) =>
        if (supportHttp2) logger.warn("JettyBuilder does not support HTTP/2 with SSL at the moment")
        new ServerConnector(jetty, sslContextFactory, http1)

      case None =>
        if (!supportHttp2) {
          new ServerConnector(jetty, http1)
        } else {
          val http2c = new HTTP2CServerConnectionFactory(jettyHttpConfiguration)
          new ServerConnector(jetty, http1, http2c)
        }
    }
  }

  def resource: Resource[F, Server] = {
    // If threadPoolResourceOption is None, then use the value of
    // threadPool.
    val threadPoolR: Resource[F, ThreadPool] =
      threadPoolResourceOption.getOrElse(
        Resource.pure(threadPool)
      )
    val serverR: ThreadPool => Resource[F, Server] = (threadPool: ThreadPool) =>
      JettyLifeCycle
        .lifeCycleAsResource[F, JServer](
          F.delay {
            val jetty = new JServer(threadPool)
            val context = new ServletContextHandler()

            context.setContextPath("/")

            jetty.setHandler(context)

            val connector = getConnector(jetty)

            connector.setHost(socketAddress.getHostString)
            connector.setPort(socketAddress.getPort)
            connector.setIdleTimeout(if (idleTimeout.isFinite) idleTimeout.toMillis else -1)
            jetty.addConnector(connector)

            // Jetty graceful shutdown does not work without a stats handler
            val stats = new StatisticsHandler
            stats.setHandler(jetty.getHandler)
            jetty.setHandler(stats)

            jetty.setStopTimeout(shutdownTimeout match {
              case d: FiniteDuration => d.toMillis
              case _ => 0L
            })

            for ((mount, i) <- mounts.zipWithIndex)
              mount.f(context, i, this)

            jetty
          }
        )
        .map((jetty: JServer) =>
          new Server {
            lazy val address: InetSocketAddress = {
              val host = socketAddress.getHostString
              val port = jetty.getConnectors()(0).asInstanceOf[ServerConnector].getLocalPort
              new InetSocketAddress(host, port)
            }

            lazy val isSecure: Boolean = sslConfig.isSecure
          }
        )
    for {
      threadPool <- threadPoolR
      server <- serverR(threadPool)
      _ <- Resource.eval(banner.traverse_(value => F.delay(logger.info(value))))
      _ <- Resource.eval(
        F.delay(
          logger.info(
            s"http4s v${BuildInfo.version} on Jetty v${JServer.getVersion} started at ${server.baseUri}"
          )
        )
      )
    } yield server
  }
}

object JettyBuilder {
  def apply[F[_]: ConcurrentEffect] =
    new JettyBuilder[F](
      socketAddress = defaults.IPv4SocketAddress,
      threadPool = LazyThreadPool.newLazyThreadPool,
      threadPoolResourceOption = Some(
        JettyThreadPools.default[F]
      ),
      idleTimeout = defaults.IdleTimeout,
      asyncTimeout = defaults.ResponseTimeout,
      shutdownTimeout = defaults.ShutdownTimeout,
      servletIo = ServletContainer.DefaultServletIo,
      sslConfig = NoSsl,
      mounts = Vector.empty,
      serviceErrorHandler = DefaultServiceErrorHandler,
      supportHttp2 = false,
      banner = defaults.Banner,
      jettyHttpConfiguration = defaultJettyHttpConfiguration,
    )

  private sealed trait SslConfig {
    def makeSslContextFactory: Option[SslContextFactory.Server]
    def isSecure: Boolean
  }

  private class KeyStoreBits(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[StoreInfo],
      clientAuth: SSLClientAuthMode,
  ) extends SslConfig {
    def makeSslContextFactory: Option[SslContextFactory.Server] = {
      val sslContextFactory = new SslContextFactory.Server()
      sslContextFactory.setKeyStorePath(keyStore.path)
      sslContextFactory.setKeyStorePassword(keyStore.password)
      sslContextFactory.setKeyManagerPassword(keyManagerPassword)
      sslContextFactory.setProtocol(protocol)
      updateClientAuth(sslContextFactory, clientAuth)

      trustStore.foreach { trustManagerBits =>
        sslContextFactory.setTrustStorePath(trustManagerBits.path)
        sslContextFactory.setTrustStorePassword(trustManagerBits.password)
      }
      sslContextFactory.some
    }
    def isSecure = true
  }

  private class ContextWithClientAuth(sslContext: SSLContext, clientAuth: SSLClientAuthMode)
      extends SslConfig {
    def makeSslContextFactory: Option[SslContextFactory.Server] = {
      val sslContextFactory = new SslContextFactory.Server()
      sslContextFactory.setSslContext(sslContext)
      updateClientAuth(sslContextFactory, clientAuth)
      sslContextFactory.some
    }
    def isSecure = true
  }

  private class ContextOnly(sslContext: SSLContext) extends SslConfig {
    def makeSslContextFactory: Option[SslContextFactory.Server] = {
      val sslContextFactory = new SslContextFactory.Server()
      sslContextFactory.setSslContext(sslContext)
      sslContextFactory.some
    }
    def isSecure = true
  }

  private class ContextWithParameters(sslContext: SSLContext, sslParameters: SSLParameters)
      extends SslConfig {
    def makeSslContextFactory: Option[SslContextFactory.Server] = {
      val sslContextFactory = new SslContextFactory.Server()
      sslContextFactory.setSslContext(sslContext)
      sslContextFactory.customize(sslParameters)
      sslContextFactory.some
    }
    def isSecure = true
  }

  private object NoSsl extends SslConfig {
    def makeSslContextFactory: Option[SslContextFactory.Server] = None
    def isSecure = false
  }

  private def updateClientAuth(
      sslContextFactory: SslContextFactory.Server,
      clientAuthMode: SSLClientAuthMode,
  ): Unit =
    clientAuthMode match {
      case SSLClientAuthMode.NotRequested =>
        sslContextFactory.setWantClientAuth(false)
        sslContextFactory.setNeedClientAuth(false)

      case SSLClientAuthMode.Requested =>
        sslContextFactory.setWantClientAuth(true)

      case SSLClientAuthMode.Required =>
        sslContextFactory.setNeedClientAuth(true)
    }

  /** The default [[org.eclipse.jetty.server.HttpConfiguration]] to use with jetty. */
  private val defaultJettyHttpConfiguration: HttpConfiguration = {
    val config: HttpConfiguration = new HttpConfiguration()
    config.setRequestHeaderSize(defaults.MaxHeadersSize)
    config
  }
}

private final case class Mount[F[_]](f: (ServletContextHandler, Int, JettyBuilder[F]) => Unit)
