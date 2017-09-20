package org.http4s
package server
package tomcat

import cats.effect._
import java.util
import java.net.InetSocketAddress
import javax.servlet.{DispatcherType, Filter}
import javax.servlet.http.HttpServlet
import org.apache.catalina.{Context, Lifecycle, LifecycleEvent, LifecycleListener}
import org.apache.catalina.startup.Tomcat
import org.apache.coyote.AbstractProtocol
import org.apache.tomcat.util.descriptor.web.{FilterDef, FilterMap}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.servlet.{Http4sServlet, ServletContainer, ServletIo}
import org.http4s.util.threads.executionContextToExecutor
import org.log4s.getLogger
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

sealed class TomcatBuilder[F[_]: Effect] private (
    socketAddress: InetSocketAddress,
    private val executionContext: Option[ExecutionContext],
    private val idleTimeout: Duration,
    private val asyncTimeout: Duration,
    private val servletIo: ServletIo[F],
    sslBits: Option[KeyStoreBits],
    mounts: Vector[Mount[F]],
    private val serviceErrorHandler: ServiceErrorHandler[F]
) extends ServletContainer[F]
    with ServerBuilder[F]
    with IdleTimeoutSupport[F]
    with SSLKeyStoreSupport[F] {

  private val F = Effect[F]
  type Self = TomcatBuilder[F]

  private[this] val logger = getLogger(classOf[TomcatBuilder[F]])

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      executionContext: Option[ExecutionContext] = executionContext,
      idleTimeout: Duration = idleTimeout,
      asyncTimeout: Duration = asyncTimeout,
      servletIo: ServletIo[F] = servletIo,
      sslBits: Option[KeyStoreBits] = sslBits,
      mounts: Vector[Mount[F]] = mounts,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler
  ): Self =
    new TomcatBuilder(
      socketAddress,
      executionContext,
      idleTimeout,
      asyncTimeout,
      servletIo,
      sslBits,
      mounts,
      serviceErrorHandler)

  override def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[StoreInfo],
      clientAuth: Boolean): Self =
    copy(
      sslBits = Some(KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)))

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  /** Uses the specified execution context as the Tomcat executor. Lacks
    * the monitoring of a Catalina executor, but works as the standard in
    * the Scala ecosystem.
    *
    * Does not work with all Catalina protocols.  Will fall back to
    * the Tomcat internal executor if it can't be set.
    */
  override def withExecutionContext(executionContext: ExecutionContext): Self =
    copy(executionContext = Some(executionContext))

  /** Uses the default internal executor. */
  def withInternalExecutor: Self =
    copy(executionContext = None)

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

  override def mountService(service: HttpService[F], prefix: String): Self =
    copy(mounts = mounts :+ Mount[F] { (ctx, index, builder) =>
      val servlet = new Http4sServlet(
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
  override def withIdleTimeout(idleTimeout: Duration): Self =
    copy(idleTimeout = idleTimeout)

  override def withAsyncTimeout(asyncTimeout: Duration): Self =
    copy(asyncTimeout = asyncTimeout)

  override def withServletIo(servletIo: ServletIo[F]): Self =
    copy(servletIo = servletIo)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  override def start: F[Server[F]] = F.delay {
    val tomcat = new Tomcat

    tomcat.addContext("", getClass.getResource("/").getPath)

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
    conn.setAttribute(
      "connection_pool_timeout",
      if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0)

    executionContext.foreach { ec =>
      conn.getProtocolHandler match {
        case p: AbstractProtocol[_] =>
          // It would be highly unusual for
          p.setExecutor(executionContextToExecutor(ec))
        case p =>
          logger.warn(s"Could not set executor on protocol handler $p. Using internal executor.")
      }
    }

    val rootContext = tomcat.getHost.findChild("").asInstanceOf[Context]
    for ((mount, i) <- mounts.zipWithIndex)
      mount.f(rootContext, i, this)

    tomcat.start()

    new Server[F] {
      override def shutdown: F[Unit] =
        F.delay {
          tomcat.stop()
          tomcat.destroy()
        }

      override def onShutdown(f: => Unit): this.type = {
        tomcat.getServer.addLifecycleListener(new LifecycleListener {
          override def lifecycleEvent(event: LifecycleEvent): Unit =
            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getLifecycle))
              f
        })
        this
      }

      lazy val address: InetSocketAddress = {
        val host = socketAddress.getHostString
        val port = tomcat.getConnector.getLocalPort
        new InetSocketAddress(host, port)
      }
    }
  }
}

object TomcatBuilder {

  def apply[F[_]: Effect]: TomcatBuilder[F] =
    new TomcatBuilder[F](
      socketAddress = ServerBuilder.DefaultSocketAddress,
      executionContext = None,
      idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
      asyncTimeout = AsyncTimeoutSupport.DefaultAsyncTimeout,
      servletIo = ServletContainer.DefaultServletIo[F],
      sslBits = None,
      mounts = Vector.empty,
      serviceErrorHandler = DefaultServiceErrorHandler
    )
}

private final case class Mount[F[_]](f: (Context, Int, TomcatBuilder[F]) => Unit)
