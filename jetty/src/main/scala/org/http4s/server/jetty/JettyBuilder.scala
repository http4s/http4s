package org.http4s
package server
package jetty

import java.net.InetSocketAddress
import java.util
import javax.net.ssl.SSLContext
import javax.servlet.http.HttpServlet
import javax.servlet.{DispatcherType, Filter}

import cats.effect._
import org.eclipse.jetty.server.{ServerConnector, Server => JServer, _}
import org.eclipse.jetty.servlet.{FilterHolder, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.servlet.{Http4sServlet, ServletContainer, ServletIo}
import org.http4s.util.threads._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

sealed class JettyBuilder[F[_]: Effect] private (
  socketAddress: InetSocketAddress,
  private val executionContext: ExecutionContext,
  private val idleTimeout: Duration,
  private val asyncTimeout: Duration,
  private val servletIo: ServletIo[F],
  sslBits: Option[SSLConfig],
  mounts: Vector[Mount[F]]
)
  extends ServletContainer[F]
    with ServerBuilder[F]
    with IdleTimeoutSupport[F]
    with SSLKeyStoreSupport[F]
    with SSLContextSupport[F] {

  private val F = Effect[F]
  type Self = JettyBuilder[F]

  private def copy(
    socketAddress: InetSocketAddress = socketAddress,
    executionContext: ExecutionContext = executionContext,
    idleTimeout: Duration = idleTimeout,
    asyncTimeout: Duration = asyncTimeout,
    servletIo: ServletIo[F] = servletIo,
    sslBits: Option[SSLConfig] = sslBits,
    mounts: Vector[Mount[F]] = mounts
  ): Self =
    new JettyBuilder(socketAddress, executionContext, idleTimeout, asyncTimeout, servletIo, sslBits, mounts)

  override def withSSL(
    keyStore: StoreInfo,
    keyManagerPassword: String,
    protocol: String,
    trustStore: Option[StoreInfo],
    clientAuth: Boolean
  ): Self = {
    copy(sslBits = Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))
  }

  override def withSSLContext(sslContext: SSLContext, clientAuth: Boolean): Self = {
    copy(sslBits = Some(SSLContextBits(sslContext, clientAuth)))
  }

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)


  override def withExecutionContext(executionContext: ExecutionContext): Self =
    copy(executionContext = executionContext)

  override def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): Self =
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

  override def mountService(service: HttpService[F], prefix: String): Self =
    copy(mounts = mounts :+ Mount[F] { (context, index, builder) =>
      val servlet = new Http4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
        executionContext = builder.executionContext
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

  private def getConnector(jetty: JServer): ServerConnector = {
    def serverConnector(sslContextFactory: SslContextFactory) = {
      // SSL HTTP Configuration
      val https_config = new HttpConfiguration()

      https_config.setSecureScheme("https")
      https_config.setSecurePort(socketAddress.getPort)
      https_config.addCustomizer(new SecureRequestCustomizer())

      val connectionFactory = new HttpConnectionFactory(https_config)
      new ServerConnector(jetty, new SslConnectionFactory(sslContextFactory,
        org.eclipse.jetty.http.HttpVersion.HTTP_1_1.asString()
      ),
        connectionFactory
      )
    }

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

        serverConnector(sslContextFactory)

      case Some(SSLContextBits(sslContext, clientAuth)) =>
        val sslContextFactory = new SslContextFactory()
        sslContextFactory.setSslContext(sslContext)
        sslContextFactory.setNeedClientAuth(clientAuth)

        serverConnector(sslContextFactory)

      case None =>
        val connectionFactory = new HttpConnectionFactory
        new ServerConnector(jetty, connectionFactory)
    }
  }

  def start: F[Server[F]] = F.delay {
    val threadPool = new QueuedThreadPool
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

    new Server[F] {
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
    }
  }
}

object JettyBuilder {
  def apply[F[_]: Effect] = new JettyBuilder[F](
    socketAddress = ServerBuilder.DefaultSocketAddress,
    executionContext = DefaultExecutionContext,
    idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
    asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
    servletIo = ServletContainer.DefaultServletIo,
    sslBits = None,
    mounts = Vector.empty
  )
}

private final case class Mount[F[_]](f: (ServletContextHandler, Int, JettyBuilder[F]) => Unit)
