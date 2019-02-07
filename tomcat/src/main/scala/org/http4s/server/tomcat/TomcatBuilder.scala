package org.http4s
package server
package tomcat

import cats.effect._
import java.net.InetSocketAddress
import java.util
import java.util.concurrent.Executor
import javax.servlet.http.HttpServlet
import javax.servlet.{DispatcherType, Filter}
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.util.ServerInfo
import org.apache.coyote.AbstractProtocol
import org.apache.tomcat.util.descriptor.web.{FilterDef, FilterMap}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.servlet.{AsyncHttp4sServlet, ServletContainer, ServletIo}
import org.log4s.getLogger
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration._

sealed class TomcatBuilder[F[_]] private (
    socketAddress: InetSocketAddress,
    externalExecutor: Option[Executor],
    private val idleTimeout: Duration,
    private val asyncTimeout: Duration,
    private val servletIo: ServletIo[F],
    sslBits: Option[KeyStoreBits],
    mounts: Vector[Mount[F]],
    private val serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String]
)(implicit protected val F: ConcurrentEffect[F])
    extends ServletContainer[F]
    with ServerBuilder[F] {

  type Self = TomcatBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      externalExecutor: Option[Executor] = externalExecutor,
      idleTimeout: Duration = idleTimeout,
      asyncTimeout: Duration = asyncTimeout,
      servletIo: ServletIo[F] = servletIo,
      sslBits: Option[KeyStoreBits] = sslBits,
      mounts: Vector[Mount[F]] = mounts,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: immutable.Seq[String] = banner
  ): Self =
    new TomcatBuilder(
      socketAddress,
      externalExecutor,
      idleTimeout,
      asyncTimeout,
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
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self =
    copy(
      sslBits = Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  /** Replace the protocol handler's internal executor with a custom, external executor */
  def withExternalExecutor(executor: Executor): TomcatBuilder[F] =
    copy(externalExecutor = Some(executor))

  /** Use Tomcat's internal executor */
  def withInternalExecutor: TomcatBuilder[F] =
    copy(externalExecutor = None)

  override def mountServlet(
      servlet: HttpServlet,
      urlMapping: String,
      name: Option[String] = None): Self =
    copy(mounts = mounts :+ Mount[F] { (ctx, index, _) =>
      val servletName = name.getOrElse(s"servlet-$index")
      val wrapper = Tomcat.addServlet(ctx, servletName, servlet)
      wrapper.addMapping(urlMapping)
      wrapper.setAsyncSupported(true)
    })

  override def mountFilter(
      filter: Filter,
      urlMapping: String,
      name: Option[String],
      dispatches: util.EnumSet[DispatcherType]): Self =
    copy(mounts = mounts :+ Mount[F] { (ctx, index, _) =>
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

  def mountService(service: HttpRoutes[F], prefix: String): Self =
    copy(mounts = mounts :+ Mount[F] { (ctx, index, builder) =>
      val servlet = new AsyncHttp4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
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
  def withIdleTimeout(idleTimeout: Duration): Self =
    copy(idleTimeout = idleTimeout)

  def withAsyncTimeout(asyncTimeout: Duration): Self =
    copy(asyncTimeout = asyncTimeout)

  override def withServletIo(servletIo: ServletIo[F]): Self =
    copy(servletIo = servletIo)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  def withBanner(banner: immutable.Seq[String]): Self =
    copy(banner = banner)

  override def resource: Resource[F, Server[F]] =
    Resource(F.delay {
      val tomcat = new Tomcat

      val docBase = getClass.getResource("/") match {
        case null => null
        case resource => resource.getPath
      }
      tomcat.addContext("", docBase)

      val conn = tomcat.getConnector()

      sslBits.foreach {
        sslBits =>
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
      conn.setAttribute(
        "connection_pool_timeout",
        if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0)

      externalExecutor.foreach { ee =>
        conn.getProtocolHandler match {
          case p: AbstractProtocol[_] =>
            p.setExecutor(ee)
          case _ =>
            logger.warn("Could not set external executor. Defaulting to internal")
        }
      }

      val rootContext = tomcat.getHost.findChild("").asInstanceOf[Context]
      for ((mount, i) <- mounts.zipWithIndex)
        mount.f(rootContext, i, this)

      tomcat.start()

      val server = new Server[F] {
        lazy val address: InetSocketAddress = {
          val host = socketAddress.getHostString
          val port = tomcat.getConnector.getLocalPort
          new InetSocketAddress(host, port)
        }

        lazy val isSecure: Boolean = sslBits.isDefined
      }

      val shutdown = F.delay {
        tomcat.stop()
        tomcat.destroy()
      }

      banner.foreach(logger.info(_))
      val tomcatVersion = ServerInfo.getServerInfo.split("/") match {
        case Array(_, version) => version
        case _ => ServerInfo.getServerInfo // well, we tried
      }
      logger.info(
        s"http4s v${BuildInfo.version} on Tomcat v${tomcatVersion} started at ${server.baseUri}")

      server -> shutdown
    })
}

object TomcatBuilder {

  def apply[F[_]: ConcurrentEffect]: TomcatBuilder[F] =
    new TomcatBuilder[F](
      socketAddress = defaults.SocketAddress,
      externalExecutor = None,
      idleTimeout = defaults.IdleTimeout,
      asyncTimeout = defaults.AsyncTimeout,
      servletIo = ServletContainer.DefaultServletIo[F],
      sslBits = None,
      mounts = Vector.empty,
      serviceErrorHandler = DefaultServiceErrorHandler,
      banner = defaults.Banner
    )
}

private final case class Mount[F[_]](f: (Context, Int, TomcatBuilder[F]) => Unit)
