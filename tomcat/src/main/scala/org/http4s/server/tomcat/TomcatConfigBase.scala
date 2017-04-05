package org.http4s
package server
package tomcat

import java.net.InetSocketAddress
import java.util.{EnumSet, UUID}
import javax.servlet.{Filter, DispatcherType, ServletContext, ServletContainerInitializer}
import javax.servlet.http.HttpServlet
import java.util.concurrent.ExecutorService

import org.apache.catalina.{Context, Lifecycle, LifecycleEvent, LifecycleListener}
import org.apache.catalina.connector.Connector
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.util.ServerInfo
import org.apache.tomcat.util.descriptor.web.{FilterMap, FilterDef}
import org.http4s.internal.compatibility._
import org.http4s.internal.kestrel._
import org.http4s.servlet.{ Http4sServlet, Http4sServletConfig, ServletContainer, ServletIo }
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.servlet.{ServletContainer, Http4sServlet}
import org.http4s.tls.{ClientAuth, TlsConfig}
import org.http4s.util.DefaultBanner
import org.http4s.util.threads.DefaultPool
import org.log4s.getLogger

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

/** Configures an embedded Tomcat server. */
private[tomcat] abstract class TomcatConfigBase { self: TomcatConfig =>
  private val logger = getLogger

  /** Returns a task to start a Jetty server.  Call one of the
   * `.unsafePerform` methods on the task to start the server.
   */
  def start: Task[Server] =
    Task.delay {
      val tomcat = new Tomcat
      configureTomcat(tomcat)
      tomcat.getService.findConnectors.headOption match {
        case Some(connector) =>
          // Make the first connector the default connector
          tomcat.setConnector(connector)
        case None =>
          logger.warn("No connector was added to Tomcat. Creating a default connector.")
          tomcat.getConnector()
      }
      tomcat.start()
      banner.foreach(_.lines.foreach(logger.info(_)))
      val tomcatVersion = ServerInfo.getServerInfo.replace("Apache Tomcat/", "")
      logger.info(s"Started http4s-${BuildInfo.version} on tomcat-${tomcatVersion}")

      new Server {
        override def shutdown: Task[Unit] =
          Task.delay {
            tomcat.stop()
            tomcat.destroy()
          }

        override def onShutdown(f: => Unit): this.type = {
          tomcat.getServer.addLifecycleListener(new LifecycleListener {
            override def lifecycleEvent(event: LifecycleEvent): Unit = {
              if (Lifecycle.AFTER_STOP_EVENT.equals(event.getLifecycle))
                f
            }
          })
          this
        }

        lazy val address: InetSocketAddress = {
          val connector = tomcat.getConnector
          val host = connector.getAttribute("host").toString
          val port = connector.getLocalPort
          new InetSocketAddress(host, port)
        }
      }
    }

  /**
   * Adds a configuration step to the Tomcat server.
   *
   * @param `f` a function to run before the server is started in the
   * `start` task
   */
  def configure(f: Tomcat => Unit): TomcatConfig =
    withConfigureTomcat { tomcat: Tomcat => configureTomcat(tomcat); f(tomcat) }

  def bindHttp(
    port: Int = 8080,
    host: String = "0.0.0.0"
  ): TomcatConfig =
    configure { server =>
      server.getService.addConnector(
        new Connector("HTTP/1.1")
          .tap(_.setAttribute("address", host))
          .tap(_.setPort(port))
      )
    }

  def bindHttps(
    port: Int = 8443,
    host: String = "0.0.0.0",
    tlsConfig: TlsConfig
  ): TomcatConfig =
    configure { server =>
      val connector = new Connector("HTTP/1.1")
        .tap(_.setAttribute("address", host))
        .tap(_.setPort(port))
        .tap(_.setSecure(true))
        .tap(_.setScheme("https"))
        .tap(_.setAttribute("SSLEnabled", true))
      tlsConfig.keyStore.foreach(connector.setAttribute("keystoreFile", _))
      tlsConfig.keyStorePassword.foreach(connector.setAttribute("keystorePass", _))
      tlsConfig.keyManagerPassword.foreach(connector.setAttribute("keyPass", _))
      connector.setAttribute("sslProtocol", tlsConfig.protocol)
      connector.setAttribute("clientAuth", tlsConfig.clientAuth match {
        case ClientAuth.Need => "true"
        case ClientAuth.Want => "want"
        case ClientAuth.None => "false"
      })
      tlsConfig.trustStore.foreach(connector.setAttribute("truststoreFile", _))
      tlsConfig.trustStorePassword.foreach(connector.setAttribute("truststorePass", _))
      server.getService.addConnector(connector)
    }

  private def getOrCreateRootContext(server: Tomcat) =
    server.getHost.findChild("") match {
      case ctx: Context => ctx
      case null => server.addContext("", getClass.getResource("/").getPath)
    }

  def mountServlet(
    servlet: HttpServlet,
    urlMapping: String,
    servletName: String = s"servlet-${UUID.randomUUID}"
  ): TomcatConfig =
    configure { server =>
      val rootContext = getOrCreateRootContext(server)
      val wrapper = Tomcat.addServlet(rootContext, servletName, servlet)
      wrapper.addMapping(urlMapping)
      wrapper.setAsyncSupported(true)
    }

  def mountFilter(
    filter: Filter,
    urlMapping: String = "/*",
    dispatches: EnumSet[DispatcherType] = EnumSet.of(DispatcherType.REQUEST),
    filterName: String = s"filter-${UUID.randomUUID}"
  ): TomcatConfig =
    configure { server =>
      val rootContext = getOrCreateRootContext(server)

      val filterDef = new FilterDef
      filterDef.setFilterName(filterName)
      filterDef.setFilter(filter)
      filterDef.setAsyncSupported(true.toString)
      rootContext.addFilterDef(filterDef)

      val filterMap = new FilterMap
      filterMap.setFilterName(filterName)
      filterMap.addURLPattern(urlMapping)
      dispatches.asScala.foreach { dispatcher =>
        filterMap.setDispatcher(dispatcher.name)
      }
      rootContext.addFilterMap(filterMap)
    }

  def mountService(
    service: HttpService,
    prefix: String = "/",
    http4sServletConfig: Http4sServletConfig = Http4sServletConfig.default,
    servletName: String = s"http4s-service-${UUID.randomUUID}"
  ): TomcatConfig = {
    val servlet = new Http4sServlet(service, http4sServletConfig)
    val urlMapping = ServletContainer.prefixMapping(prefix)
    mountServlet(servlet, urlMapping)
  }
}
