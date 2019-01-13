package org.http4s
package server
package jetty

import cats.effect._
import java.net.InetSocketAddress
import java.util
import javax.net.ssl.SSLContext
import javax.servlet.{DispatcherType, Filter}
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{ServerConnector, Server => JServer}
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.servlet.{FilterHolder, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.component.{AbstractLifeCycle, LifeCycle}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.{QueuedThreadPool, ThreadPool}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.servlet.{AsyncHttp4sServlet, ServletContainer, ServletIo}
import org.log4s.getLogger
import scala.collection.immutable
import scala.concurrent.duration._

sealed class JettyBuilder[F[_]] private (
    socketAddress: InetSocketAddress,
    threadPool: ThreadPool,
    private val idleTimeout: Duration,
    private val asyncTimeout: Duration,
    shutdownTimeout: Duration,
    private val servletIo: ServletIo[F],
    sslBits: Option[SSLConfig],
    mounts: Vector[Mount[F]],
    private val serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String]
)(implicit protected val F: ConcurrentEffect[F])
    extends ServletContainer[F]
    with ServerBuilder[F] {

  type Self = JettyBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      threadPool: ThreadPool = threadPool,
      idleTimeout: Duration = idleTimeout,
      asyncTimeout: Duration = asyncTimeout,
      shutdownTimeout: Duration = shutdownTimeout,
      servletIo: ServletIo[F] = servletIo,
      sslBits: Option[SSLConfig] = sslBits,
      mounts: Vector[Mount[F]] = mounts,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: immutable.Seq[String] = banner
  ): Self =
    new JettyBuilder(
      socketAddress,
      threadPool,
      idleTimeout,
      asyncTimeout,
      shutdownTimeout,
      servletIo,
      sslBits,
      mounts,
      serviceErrorHandler,
      banner)

  def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String = "TLS",
      trustStore: Option[StoreInfo] = None,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested
  ): Self =
    copy(
      sslBits = Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))

  def withSSLContext(
      sslContext: SSLContext,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self =
    copy(sslBits = Some(SSLContextBits(sslContext, clientAuth)))

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  def withThreadPool(threadPool: ThreadPool): JettyBuilder[F] =
    copy(threadPool = threadPool)

  override def mountServlet(
      servlet: HttpServlet,
      urlMapping: String,
      name: Option[String] = None): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, _) =>
      val servletName = name.getOrElse(s"servlet-$index")
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  override def mountFilter(
      filter: Filter,
      urlMapping: String,
      name: Option[String],
      dispatches: util.EnumSet[DispatcherType]
  ): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, _) =>
      val filterName = name.getOrElse(s"filter-$index")
      val filterHolder = new FilterHolder(filter)
      filterHolder.setName(filterName)
      context.addFilter(filterHolder, urlMapping, dispatches)
    })

  def mountService(service: HttpRoutes[F], prefix: String): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, builder) =>
      val servlet = new AsyncHttp4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
        serviceErrorHandler = builder.serviceErrorHandler
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
    * will wait this long before a forcible stop. */
  def withShutdownTimeout(shutdownTimeout: Duration): Self =
    copy(shutdownTimeout = shutdownTimeout)

  override def withServletIo(servletIo: ServletIo[F]): Self =
    copy(servletIo = servletIo)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  def withBanner(banner: immutable.Seq[String]): Self =
    copy(banner = banner)

  private def getConnector(jetty: JServer): ServerConnector = {
    def httpsConnector(sslContextFactory: SslContextFactory) =
      new ServerConnector(
        jetty,
        sslContextFactory
      )

    sslBits match {
      case Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)) =>
        // SSL Context Factory
        val sslContextFactory = new SslContextFactory()
        sslContextFactory.setKeyStorePath(keyStore.path)
        sslContextFactory.setKeyStorePassword(keyStore.password)
        sslContextFactory.setKeyManagerPassword(keyManagerPassword)
        sslContextFactory.setProtocol(protocol)
        updateClientAuth(sslContextFactory, clientAuth)

        trustStore.foreach { trustManagerBits =>
          sslContextFactory.setTrustStorePath(trustManagerBits.path)
          sslContextFactory.setTrustStorePassword(trustManagerBits.password)
        }

        httpsConnector(sslContextFactory)

      case Some(SSLContextBits(sslContext, clientAuth)) =>
        val sslContextFactory = new SslContextFactory()
        sslContextFactory.setSslContext(sslContext)
        updateClientAuth(sslContextFactory, clientAuth)

        httpsConnector(sslContextFactory)

      case None =>
        new ServerConnector(jetty)
    }
  }

  private def updateClientAuth(
      sslContextFactory: SslContextFactory,
      clientAuthMode: SSLClientAuthMode): Unit =
    clientAuthMode match {
      case SSLClientAuthMode.NotRequested =>
        sslContextFactory.setWantClientAuth(false)
        sslContextFactory.setNeedClientAuth(false)

      case SSLClientAuthMode.Requested =>
        sslContextFactory.setWantClientAuth(true)

      case SSLClientAuthMode.Required =>
        sslContextFactory.setNeedClientAuth(true)
    }

  def resource: Resource[F, Server[F]] =
    Resource(F.delay {
      val jetty = new JServer(threadPool)

      val context = new ServletContextHandler()
      context.setContextPath("/")

      jetty.setHandler(context)

      val connector = getConnector(jetty)

      connector.setHost(socketAddress.getHostString)
      connector.setPort(socketAddress.getPort)
      connector.setIdleTimeout(if (idleTimeout.isFinite()) idleTimeout.toMillis else -1)
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

      jetty.start()

      val server = new Server[F] {
        lazy val address: InetSocketAddress = {
          val host = socketAddress.getHostString
          val port = jetty.getConnectors()(0).asInstanceOf[ServerConnector].getLocalPort
          new InetSocketAddress(host, port)
        }

        lazy val isSecure: Boolean = sslBits.isDefined
      }

      banner.foreach(logger.info(_))
      logger.info(
        s"http4s v${BuildInfo.version} on Jetty v${JServer.getVersion} started at ${server.baseUri}")

      server -> shutdown(jetty)
    })

  private def shutdown(jetty: JServer): F[Unit] =
    F.async[Unit] { cb =>
      jetty.addLifeCycleListener(
        new AbstractLifeCycle.AbstractLifeCycleListener {
          override def lifeCycleStopped(ev: LifeCycle) = cb(Right(()))
          override def lifeCycleFailure(ev: LifeCycle, cause: Throwable) = cb(Left(cause))
        }
      )
      jetty.stop()
    }
}

object JettyBuilder {
  def apply[F[_]: ConcurrentEffect] = new JettyBuilder[F](
    socketAddress = defaults.SocketAddress,
    threadPool = new QueuedThreadPool(),
    idleTimeout = defaults.IdleTimeout,
    asyncTimeout = defaults.AsyncTimeout,
    shutdownTimeout = defaults.ShutdownTimeout,
    servletIo = ServletContainer.DefaultServletIo,
    sslBits = None,
    mounts = Vector.empty,
    serviceErrorHandler = DefaultServiceErrorHandler,
    banner = defaults.Banner
  )
}

private final case class Mount[F[_]](f: (ServletContextHandler, Int, JettyBuilder[F]) => Unit)
