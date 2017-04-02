package org.http4s
package server
package jetty

import java.net.InetSocketAddress
import java.util
import java.util.EnumSet
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext
import javax.servlet.http.HttpServlet
import javax.servlet.{DispatcherType, Filter}
import scala.concurrent.duration._

import org.eclipse.jetty.server.{Server => JServer, _}
import org.eclipse.jetty.servlet.{FilterHolder, ServletHolder, ServletContextHandler}
import org.eclipse.jetty.util.Jetty.{VERSION => JettyVersion}
import org.eclipse.jetty.util.component.AbstractLifeCycle.AbstractLifeCycleListener
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.http4s.internal.compatibility._
import org.http4s.internal.kestrel._
import org.http4s.servlet.{ServletIo, ServletContainer, Http4sServlet}
import org.http4s.util.DefaultBanner
import org.log4s.getLogger
import scalaz.concurrent.Task

/** Configures an embedded Jetty server. */
final class JettyConfig private[jetty] (config: JServer => Unit, banner: List[String]) { self =>
  private[this] val log = getLogger

  /** Returns a task to start a Jetty server.  Call one of the
   * `.unsafePerform` methods on the task to start the server.
   *
   * @param jetty A Jetty server instance to be configured
   */
  def start(jetty: JServer = new JServer): Task[Server] =
    Task.delay {
      config(jetty)
      jetty.start()
      banner.foreach(_.lines.foreach(log.info(_)))
      log.info(s"Started http4s-${BuildInfo.version} on jetty-${JettyVersion}")

      new Server {
        val address =
          jetty.getConnectors.collectFirst {
            case connector: NetworkConnector =>
              val host = Option(connector.getHost).getOrElse("0.0.0.0")
              val port = connector.getLocalPort
              new InetSocketAddress(host, port)
          }.getOrElse(new InetSocketAddress("0.0.0.0", 0))

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
  def unsafeRun(jetty: JServer = new JServer): Server =
    start(jetty).unsafePerformSync

  /**
   * Adds a configuration step to the Jetty server.
   *
   * @param `f` a function to run before the server is started in the
   * `start` task
   */
  def configure(f: JServer => Unit): JettyConfig =
    new JettyConfig(server => f(server.tap(config)), banner)

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
    host: String = "0.0.0.0",
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
   * @param needClientAuth set true to enable client
   * authentication. If true, The SSL context will need to be
   * configured with an appropriate trust manager.
   * @param executor an executor to run the connector on.  If
   * unspecified, defaults to the Jetty thread pool
   */
  def bindHttps(
    port: Int = 8443,
    host: String = "0.0.0.0",
    sslContext: SSLContext = SSLContext.getDefault,
    needClientAuth: Boolean = false,
    executor: Executor = null
  ): JettyConfig =
    configure { server =>
      val sslContextFactory = new SslContextFactory()
        .tap(_.setSslContext(sslContext))
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
  def mountServlet(servlet: HttpServlet, urlMapping: String = "/*"): JettyConfig =
    configureServletContextHandler { handler =>
      handler.addServlet(new ServletHolder(servlet), urlMapping)
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
    dispatches: util.EnumSet[DispatcherType] = EnumSet.of(DispatcherType.REQUEST)
  ): JettyConfig =
    configureServletContextHandler { handler =>
      handler.addFilter(new FilterHolder(filter), urlMapping, dispatches)
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
    executor: Option[ExecutorService] = None,
    asyncTimeout: Duration = 30.seconds,
    servletIo: ServletIo = ServletContainer.DefaultServletIo
  ): JettyConfig = {
    val urlMapping = ServletContainer.prefixMapping(prefix)
    mountServlet(new Http4sServlet(service, asyncTimeout, executor, servletIo), urlMapping)
  }

  /** Configure the server startup banner text
   *
   * @param banner the default banner
   */
  def withBanner(banner: List[String]) =
    new JettyConfig(config, banner = banner)

  /** Disable the server startup banner text */
  def noBanner: JettyConfig =
    withBanner(Nil)
}

object JettyConfig {
  val default: JettyConfig =
    new JettyConfig(_ => (), DefaultBanner)
}
