package org.http4s
package server
package jetty

import com.codahale.metrics.{InstrumentedExecutorService, MetricRegistry}
import com.codahale.metrics.jetty9.{InstrumentedHandler, InstrumentedQueuedThreadPool, InstrumentedConnectionFactory}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.http4s.server.SSLSupport.{StoreInfo, SSLBits}
import org.http4s.servlet.{ServletContainer, Http4sServlet}

import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.http4s.servlet.{ServletIo, ServletContainer, Http4sServlet}

import scala.concurrent.duration._
import scalaz.concurrent.Task

import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.server.{Server => JServer, _}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}

sealed class JettyBuilder private (
  socketAddress: InetSocketAddress,
  private val serviceExecutor: ExecutorService,
  private val idleTimeout: Duration,
  private val asyncTimeout: Duration,
  private val servletIo: ServletIo,
  sslBits: Option[SSLBits],
  mounts: Vector[Mount],
  metricRegistry: Option[MetricRegistry],
  metricPrefix: String
)
  extends ServerBuilder
  with ServletContainer
  with IdleTimeoutSupport
  with SSLSupport
  with MetricsSupport
{
  type Self = JettyBuilder

  private def copy(socketAddress: InetSocketAddress = socketAddress,
                   serviceExecutor: ExecutorService = serviceExecutor,
                   idleTimeout: Duration = idleTimeout,
                   asyncTimeout: Duration = asyncTimeout,
                   servletIo: ServletIo = servletIo,
                   sslBits: Option[SSLBits] = sslBits,
                   mounts: Vector[Mount] = mounts,
                   metricRegistry: Option[MetricRegistry] = metricRegistry,
                   metricPrefix: String = metricPrefix): JettyBuilder =
    new JettyBuilder(socketAddress, serviceExecutor, idleTimeout, asyncTimeout, servletIo, sslBits, mounts, metricRegistry, metricPrefix)

  override def withSSL(keyStore: StoreInfo, keyManagerPassword: String, protocol: String, trustStore: Option[StoreInfo], clientAuth: Boolean): Self = {
    copy(sslBits = Some(SSLBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))
  }

  override def bindSocketAddress(socketAddress: InetSocketAddress): JettyBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): JettyBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): JettyBuilder =
    copy(mounts = mounts :+ Mount { (context, index, _) =>
      val servletName = name.getOrElse(s"servlet-$index")
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  override def mountService(service: HttpService, prefix: String): JettyBuilder =
    copy(mounts = mounts :+ Mount { (context, index, builder) =>
      val servlet = new Http4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
        threadPool = builder.instrumentedServiceExecutor
      )
      val servletName = s"servlet-$index"
      val urlMapping = ServletContainer.prefixMapping(prefix)
      context.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    })

  override def withIdleTimeout(idleTimeout: Duration): JettyBuilder =
    copy(idleTimeout = idleTimeout)

  override def withAsyncTimeout(asyncTimeout: Duration): JettyBuilder =
    copy(asyncTimeout = asyncTimeout)

  override def withServletIo(servletIo: ServletIo): Self =
    copy(servletIo = servletIo)

  /**
   * Collects metrics into the specified [[MetricRegistry]], including:
   * * Connections via [[InstrumentedConnectionFactory]]
   * * The Jetty thread pool via [[InstrumentedQueuedThreadPool]]
   * * HTTP responses via [[InstrumentedHandler]]
   *
   * @param metricRegistry The registry to collect metrics into..
   */
  override def withMetricRegistry(metricRegistry: MetricRegistry): Self =
    copy(metricRegistry = Some(metricRegistry))

  override def withMetricPrefix(metricPrefix: String): Self = copy(metricPrefix = metricPrefix)

  private def getConnector(jetty: JServer): ServerConnector = sslBits match {
    case Some(sslBits) =>
      // SSL Context Factory
      val sslContextFactory = new SslContextFactory()
      sslContextFactory.setKeyStorePath(sslBits.keyStore.path)
      sslContextFactory.setKeyStorePassword(sslBits.keyStore.password)
      sslContextFactory.setKeyManagerPassword(sslBits.keyManagerPassword)
      sslContextFactory.setNeedClientAuth(sslBits.clientAuth)
      sslContextFactory.setProtocol(sslBits.protocol)

      sslBits.trustStore.foreach { trustManagerBits =>
        sslContextFactory.setTrustStorePath(trustManagerBits.path)
        sslContextFactory.setTrustStorePassword(trustManagerBits.password)
      }

      // SSL HTTP Configuration
      val https_config = new HttpConfiguration()

      https_config.setSecureScheme("https")
      https_config.setSecurePort(socketAddress.getPort)
      https_config.addCustomizer(new SecureRequestCustomizer())

      val connectionFactory = instrumentConnectionFactory(new HttpConnectionFactory(https_config))
      new ServerConnector(jetty, new SslConnectionFactory(sslContextFactory,
        org.eclipse.jetty.http.HttpVersion.HTTP_1_1.asString()),
        connectionFactory)

    case None =>
      val connectionFactory = instrumentConnectionFactory(new HttpConnectionFactory)
      new ServerConnector(jetty, connectionFactory)
  }

  private def instrumentConnectionFactory(connectionFactory: ConnectionFactory) =
    metricRegistry.fold(connectionFactory) { reg =>
      val timer = reg.timer(MetricRegistry.name(metricPrefix, "connections"))
      new InstrumentedConnectionFactory(connectionFactory, timer)
    }

  private def instrumentedServiceExecutor = metricRegistry.fold(serviceExecutor) {
    new InstrumentedExecutorService(serviceExecutor, _, MetricRegistry.name(metricPrefix, "service-executor"))
  }

  def start: Task[Server] = Task.delay {
    val threadPool = metricRegistry.fold(new QueuedThreadPool)(new InstrumentedQueuedThreadPool(_))
    val jetty = new JServer(threadPool)

    val context = new ServletContextHandler()
    context.setContextPath("/")

    val handler = metricRegistry.fold[Handler](context) { reg =>
      val h = new InstrumentedHandler(reg, metricPrefix)
      h.setHandler(context)
      h
    }

    jetty.setHandler(handler)

    val connector = getConnector(jetty)

    connector.setHost(socketAddress.getHostString)
    connector.setPort(socketAddress.getPort)
    connector.setIdleTimeout(if (idleTimeout.isFinite()) idleTimeout.toMillis else -1)
    jetty.addConnector(connector)

    for ((mount, i) <- mounts.zipWithIndex)
      mount.f(context, i, this)

    jetty.start()

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        jetty.stop()
        this
      }

      override def onShutdown(f: => Unit): this.type = {
        jetty.addLifeCycleListener { new AbstractLifeCycleListener {
          override def lifeCycleStopped(event: LifeCycle): Unit = f
        }}
        this
      }
    }
  }
}

object JettyBuilder extends JettyBuilder(
  socketAddress = ServerBuilder.DefaultSocketAddress,
  serviceExecutor = ServerBuilder.DefaultServiceExecutor,
  idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
  asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
  servletIo = ServletContainer.DefaultServletIo,
  sslBits = None,
  mounts = Vector.empty,
  metricRegistry = None,
  metricPrefix = MetricsSupport.DefaultPrefix
)

private case class Mount(f: (ServletContextHandler, Int, JettyBuilder) => Unit)
