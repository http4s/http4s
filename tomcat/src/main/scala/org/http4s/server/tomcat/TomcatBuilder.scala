package org.http4s
package server
package tomcat

import java.net.InetSocketAddress
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import javax.servlet.http.HttpServlet
import javax.servlet.{Filter, DispatcherType, ServletContext, ServletContainerInitializer}
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.{Context, Lifecycle, LifecycleEvent, LifecycleListener}
import org.apache.catalina.util.ServerInfo
import org.apache.tomcat.util.descriptor.web.{FilterMap, FilterDef}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.servlet.{ServletContainer, Http4sServlet}
import org.http4s.servlet.{ServletIo, ServletContainer, Http4sServlet}
import org.http4s.util.threads.DefaultPool
import org.log4s.getLogger
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

sealed class TomcatBuilder private (
  socketAddress: InetSocketAddress,
  private val serviceExecutor: ExecutorService,
  private val idleTimeout: Duration,
  private val asyncTimeout: Duration,
  private val servletIo: ServletIo,
  sslBits: Option[KeyStoreBits],
  mounts: Vector[Mount],
  private val serviceErrorHandler: ServiceErrorHandler
)
  extends ServerBuilder
  with ServletContainer
  with IdleTimeoutSupport
  with SSLKeyStoreSupport
{
  type Self = TomcatBuilder

  private[this] val logger = getLogger

  private def copy(
    socketAddress: InetSocketAddress = socketAddress,
    serviceExecutor: ExecutorService = serviceExecutor,
    idleTimeout: Duration = idleTimeout,
    asyncTimeout: Duration = asyncTimeout,
    servletIo: ServletIo = servletIo,
    sslBits: Option[KeyStoreBits] = sslBits,
    mounts: Vector[Mount] = mounts,
    serviceErrorHandler: ServiceErrorHandler = serviceErrorHandler
  ): TomcatBuilder =
    new TomcatBuilder(socketAddress, serviceExecutor, idleTimeout, asyncTimeout, servletIo, sslBits, mounts, serviceErrorHandler)

  override def withSSL(keyStore: StoreInfo, keyManagerPassword: String, protocol: String, trustStore: Option[StoreInfo], clientAuth: Boolean): Self = {
    copy(sslBits = Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))
  }

  override def bindSocketAddress(socketAddress: InetSocketAddress): TomcatBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): TomcatBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def mountServlet(servlet: HttpServlet, urlMapping: String, name: Option[String] = None): TomcatBuilder =
    copy(mounts = mounts :+ Mount { (ctx, index, _) =>
      val servletName = name.getOrElse(s"servlet-$index")
      val wrapper = Tomcat.addServlet(ctx, servletName, servlet)
      wrapper.addMapping(urlMapping)
      wrapper.setAsyncSupported(true)
    })

  override def mountFilter(filter: Filter, urlMapping: String, name: Option[String], dispatches: EnumSet[DispatcherType]): TomcatBuilder =
    copy(mounts = mounts :+ Mount { (ctx, index, _) =>
      val filterName = name.getOrElse(s"filter-$index")

      val filterDef = new FilterDef
      filterDef.setFilterName(filterName)
      filterDef.setFilter(filter)

      filterDef.setAsyncSupported(true.toString)
      ctx.addFilterDef(filterDef)

      val filterMap = new FilterMap
      filterMap.setFilterName(filterName)
      filterMap.addURLPattern(urlMapping)
      dispatches.asScala.foreach { dispatcher =>
        filterMap.setDispatcher(dispatcher.name)
      }
      ctx.addFilterMap(filterMap)
    })

  override def mountService(service: HttpService, prefix: String): TomcatBuilder =
    copy(mounts = mounts :+ Mount { (ctx, index, builder) =>
      val servlet = new Http4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
        threadPool = builder.serviceExecutor,
        serviceErrorHandler = builder.serviceErrorHandler
      )
      val wrapper = Tomcat.addServlet(ctx, s"servlet-$index", servlet)
      wrapper.addMapping(ServletContainer.prefixMapping(prefix))
      wrapper.setAsyncSupported(true)
    })

  /*
   * Tomcat maintains connections on a fixed interval determined by the global
   * attribute worker.maintain with a default interval of 60 seconds. In the worst case the connection
   * may not timeout for an additional 59.999 seconds from the specified Duration
   */
  override def withIdleTimeout(idleTimeout: Duration): TomcatBuilder =
    copy(idleTimeout = idleTimeout)

  override def withAsyncTimeout(asyncTimeout: Duration): TomcatBuilder =
    copy(asyncTimeout = asyncTimeout)

  override def withServletIo(servletIo: ServletIo): Self =
    copy(servletIo = servletIo)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler): TomcatBuilder =
    copy(serviceErrorHandler = serviceErrorHandler)

  override def start: Task[Server] = Task.delay {
    val tomcat = new Tomcat

    val docBase = getClass.getResource("/") match {
      case null => null 
      case resource => resource.getPath
    }
    val context = tomcat.addContext("", docBase)

    val conn = tomcat.getConnector()

    sslBits.foreach { sslBits =>
      conn.setSecure(true)
      conn.setScheme("https")
      conn.setAttribute("keystoreFile", sslBits.keyStore.path)
      conn.setAttribute("keystorePass", sslBits.keyStore.password)
      conn.setAttribute("keyPass", sslBits.keyManagerPassword)

      conn.setAttribute("clientAuth", sslBits.clientAuth)
      conn.setAttribute("sslProtocol", sslBits.protocol)

      sslBits.trustStore.foreach { ts =>
        conn.setAttribute("truststoreFile", ts.path)
        conn.setAttribute("truststorePass", ts.password)
      }

      conn.setPort(socketAddress.getPort)

      conn.setAttribute("SSLEnabled", true)
    }

    conn.setAttribute("address", socketAddress.getHostString)
    conn.setPort(socketAddress.getPort)
    conn.setAttribute("connection_pool_timeout",
      if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0)

    val rootContext = tomcat.getHost.findChild("").asInstanceOf[Context]
    for ((mount, i) <- mounts.zipWithIndex)
      mount.f(rootContext, i, this)

    tomcat.start()

    val server = new Server {
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
        val host = socketAddress.getHostString
        val port = tomcat.getConnector.getLocalPort
        new InetSocketAddress(host, port)
      }
    }

    val tomcatVersion = ServerInfo.getServerInfo.split("/") match {
      case Array(_, version) => version
      case _ => ServerInfo.getServerInfo // well, we tried
    }
    logger.info(s"http4s v${BuildInfo.version} on Tomcat v${tomcatVersion} started at ${Server.baseUri(server.address, sslBits.isDefined)}")
    server
  }
}

object TomcatBuilder extends TomcatBuilder(
  socketAddress = ServerBuilder.DefaultSocketAddress,
  serviceExecutor = Strategy.DefaultExecutorService,
  idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
  asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
  servletIo = ServletContainer.DefaultServletIo,
  sslBits = None,
  mounts = Vector.empty,
  serviceErrorHandler = DefaultServiceErrorHandler
)

private final case class Mount(f: (Context, Int, TomcatBuilder) => Unit)

