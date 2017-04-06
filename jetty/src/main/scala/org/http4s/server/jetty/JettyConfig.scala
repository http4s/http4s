package org.http4s
package server
package jetty

import java.net.InetSocketAddress
import java.util
import java.util.{EnumSet, UUID}
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.servlet.{DispatcherType, Filter}
import javax.servlet.http.HttpServlet

import org.eclipse.jetty.server.{Server => JServer, _}
import org.eclipse.jetty.servlet.{FilterHolder, ServletHolder, ServletContextHandler}
import org.eclipse.jetty.util.Jetty.{VERSION => JettyVersion}
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.http4s.internal.compatibility._
import org.http4s.internal.kestrel._
import org.http4s.servlet.{ServletContainer, Http4sServlet, Http4sServletConfig}
import org.http4s.tls.ClientAuth
import org.log4s.getLogger
import scalaz.concurrent.Task

/** Configures an embedded Jetty server. */
private[jetty] abstract class JettyConfigBase { self: JettyConfig =>
  private[this] val log = getLogger

  /** Returns a task to start a Jetty server.  Call one of the
   * `.unsafePerform` methods on the task to start the server.
   *
   * @param jetty A Jetty server instance to be configured
   */
  def start: Task[Server] =
    Task.delay {
      val jetty = new JServer(threadPool.orNull)
      configureJetty(jetty)
      jetty.start()
      banner.foreach(_.lines.foreach(log.info(_)))
      log.info(s"Started http4s-${BuildInfo.version} on jetty-${JettyVersion}")

      new Server {
        val address =
          jetty.getConnectors.collectFirst {
            case connector: NetworkConnector =>
              val host = Option(connector.getHost).getOrElse("127.0.0.1")
              val port = connector.getLocalPort
              new InetSocketAddress(host, port)
          }.getOrElse(new InetSocketAddress("127.0.0.1", 8080))

        override def shutdown: Task[Unit] =
          Task.delay {
            jetty.stop()
          }

        override def onShutdown(f: => Unit): this.type = {
          jetty.addLifeCycleListener {
            new AbstractLifeCycleListener {
              override def lifeCycleStopped(event: LifeCycle): Unit = f
            }}
          this
        }
      }
    }

  /** Returns a task to start a Jetty server.  Call one of the
   * `.unsafePerform` methods on the task to start the server.
   *
   * @param jetty A Jetty server instance to be configured
   */
  def unsafeRun: Server =
    start.unsafePerformSync

  /**
   * Adds a configuration step to the Jetty server.
   *
   * @param `f` a function to run before the server is started in the
   * `start` task
   */
  def configure(f: JServer => Unit): JettyConfig =
    withConfigureJetty { jetty => configureJetty(jetty); f(jetty) }

  /**
   * Adds an HTTP connector to the Jetty server
   *
   * @param port the port to bind
   * @param host the host to bind
   * @param executor an executor to run the connector on.  If
   * unspecified, defaults to the Jetty thread pool
   */
  def bindHttp(
    port: Int = 8080,
    host: String = "127.0.0.1",
    executor: Executor = null
  ): JettyConfig =
    configure { server =>
      server.addConnector(
        new ServerConnector(server, executor, null, null, -1, -1, new HttpConnectionFactory)
          .tap(_.setPort(port))
          .tap(_.setHost(host))
      )
    }

  /**
   * Adds an HTTPS connector to the Jetty server
   *
   * @param port the port to bind
   * @param host the host to bind
   * @param sslContext the SSL context to use
   * @param clientAuth whether client auth is needed or wanted.  If
   * set to `need`, The SSL context will need to be configured with an
   * appropriate trust manager.
   * @param executor an executor to run the connector on.  If
   * unspecified, defaults to the Jetty thread pool
   */
  def bindHttps(
    port: Int = 8443,
    host: String = "127.0.0.1",
    sslContext: SSLContext = SSLContext.getDefault,
    clientAuth: ClientAuth = ClientAuth.None,
    executor: Executor = null
  ): JettyConfig =
    configure { server =>
      val sslContextFactory = new SslContextFactory()
        .tap(_.setSslContext(sslContext))
        .tap(_.setNeedClientAuth(clientAuth == ClientAuth.Need))
        .tap(_.setWantClientAuth(clientAuth == ClientAuth.Want))
      val httpsConfig = new HttpConfiguration()
        .tap(_.addCustomizer(new SecureRequestCustomizer))
      val connector = new ServerConnector(server, executor, null, null, -1, -1,
        new SslConnectionFactory(sslContextFactory, "http/1.1"),
        new HttpConnectionFactory(httpsConfig)
      )
        .tap(_.setPort(port))
        .tap(_.setHost(host))
      server.addConnector(connector)
    }

  private def configureServletContextHandler(f: ServletContextHandler => Unit): JettyConfig =
    configure { server =>
      server.getHandler match {
        case h: ServletContextHandler =>
          f(h)
        case h: Handler =>
          throw new IllegalStateException(s"Server handler was not a ServletContextHandler: h")
        case null =>
          val h = new ServletContextHandler()
            .tap(_.setContextPath("/"))
          server.setHandler(h)
          f(h)
      }
    }

  /**
   * Mounts a servlet. http4s services should prefer `mountService`.
   *
   * @param servlet The servlet to mount.
   * @param urlMapping The URL mapping for the servlet.  In typical use,
   * should end in an asterisk.  The asterisk is not implied to
   * support the full range of servlet mappings.
   */
  def mountServlet(
    servlet: HttpServlet,
    urlMapping: String,
    servletName: String = s"servlet-${UUID.randomUUID}"
  ): JettyConfig =
    configureServletContextHandler { handler =>
      handler.addServlet(new ServletHolder(servletName, servlet), urlMapping)
    }

  /**
   * Mounts a servlet. http4s services should prefer `mountService`.
   *
   * @param servlet The servlet to mount.
   * @param urlMapping The URL mapping for the filter.  In typical use,
   * should end in an asterisk.  The asterisk is not implied to
   * support the full range of servlet mappings.
   * @param dispatches The dispatcher types for the filter
   */
  def mountFilter(
    filter: Filter,
    urlMapping: String = "/*",
    dispatches: util.EnumSet[DispatcherType] = EnumSet.of(DispatcherType.REQUEST),
    filterName: String = s"filter-${UUID.randomUUID}"
  ): JettyConfig =
    configureServletContextHandler { handler =>
      handler.addFilter(new FilterHolder(filter).tap(_.setName(filterName)), urlMapping, dispatches)
    }

  /**
   * Mounts an HttpService
   *
   * @param service the service to mount
   * @param prefix the prefix to mount the service to.  Unlike urlMapping of
   * `mountServlet`, an asterisk is implied, as the HttpService is always
   * assumed to control a prefix
   * @param executor an optional executor to run the service on.  The connector's
   * thread pool is used if not specified.
   * @param asyncTimeout the async timeout for this service.  If no response is
   * complete in this time, the server generates a timeout
   * @param validservletIo the servlet IO model for the servlet that wraps
   * `service`
   */
  def mountService(
    service: HttpService,
    prefix: String = "/",
    http4sServletConfig: Http4sServletConfig = Http4sServletConfig.default,
    servletName: String = s"http4s-service-${UUID.randomUUID}"
  ): JettyConfig = {
    val urlMapping = ServletContainer.prefixMapping(prefix)
    mountServlet(new Http4sServlet(service, http4sServletConfig), urlMapping)
  }
}
