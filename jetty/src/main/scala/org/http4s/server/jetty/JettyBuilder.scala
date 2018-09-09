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
import org.eclipse.jetty.servlet.{FilterHolder, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle
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
    private val servletIo: ServletIo[F],
    sslBits: Option[SSLConfig],
    mounts: Vector[Mount[F]],
    private val serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String]
)(implicit protected val F: ConcurrentEffect[F])
    extends ServletContainer[F]
    with ServerBuilder[F]
    with IdleTimeoutSupport[F]
    with SSLKeyStoreSupport[F]
    with SSLContextSupport[F] {

  type Self = JettyBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      threadPool: ThreadPool = threadPool,
      idleTimeout: Duration = idleTimeout,
      asyncTimeout: Duration = asyncTimeout,
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
      servletIo,
      sslBits,
      mounts,
      serviceErrorHandler,
      banner)

  override def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[StoreInfo],
      clientAuth: Boolean
  ): Self =
    copy(
      sslBits = Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))

  override def withSSLContext(sslContext: SSLContext, clientAuth: Boolean): Self =
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

  override def withIdleTimeout(idleTimeout: Duration): Self =
    copy(idleTimeout = idleTimeout)

  override def withAsyncTimeout(asyncTimeout: Duration): Self =
    copy(asyncTimeout = asyncTimeout)

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
        sslContextFactory.setNeedClientAuth(clientAuth)
        sslContextFactory.setProtocol(protocol)

        trustStore.foreach { trustManagerBits =>
          sslContextFactory.setTrustStorePath(trustManagerBits.path)
          sslContextFactory.setTrustStorePassword(trustManagerBits.password)
        }

        httpsConnector(sslContextFactory)

      case Some(SSLContextBits(sslContext, clientAuth)) =>
        val sslContextFactory = new SslContextFactory()
        sslContextFactory.setSslContext(sslContext)
        sslContextFactory.setNeedClientAuth(clientAuth)

        httpsConnector(sslContextFactory)

      case None =>
        new ServerConnector(jetty)
    }
  }

  def start: F[Server[F]] = F.delay {
    val jetty = new JServer(threadPool)

    val context = new ServletContextHandler()
    context.setContextPath("/")

    jetty.setHandler(context)

    val connector = getConnector(jetty)

    connector.setHost(socketAddress.getHostString)
    connector.setPort(socketAddress.getPort)
    connector.setIdleTimeout(if (idleTimeout.isFinite()) idleTimeout.toMillis else -1)
    jetty.addConnector(connector)

    for ((mount, i) <- mounts.zipWithIndex)
      mount.f(context, i, this)

    jetty.start()

    val server = new Server[F] {
      override def shutdown: F[Unit] =
        F.delay(jetty.stop())

      override def onShutdown(f: => Unit): this.type = {
        jetty.addLifeCycleListener {
          new AbstractLifeCycleListener {
            override def lifeCycleStopped(event: LifeCycle): Unit = f
          }
        }
        this
      }

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
    server
  }
}

object JettyBuilder {
  def apply[F[_]: ConcurrentEffect] = new JettyBuilder[F](
    socketAddress = ServerBuilder.DefaultSocketAddress,
    threadPool = new QueuedThreadPool(),
    idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
    asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
    servletIo = ServletContainer.DefaultServletIo,
    sslBits = None,
    mounts = Vector.empty,
    serviceErrorHandler = DefaultServiceErrorHandler,
    banner = ServerBuilder.DefaultBanner
  )
}

private final case class Mount[F[_]](f: (ServletContextHandler, Int, JettyBuilder[F]) => Unit)
