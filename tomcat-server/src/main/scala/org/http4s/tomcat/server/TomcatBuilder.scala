/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package tomcat
package server

import cats.effect._
import cats.effect.std.Dispatcher

import java.net.InetSocketAddress
import java.util
import java.util.concurrent.Executor
import javax.servlet.http.HttpServlet
import javax.servlet.{DispatcherType, Filter}
import org.apache.catalina.Context
import org.apache.catalina.connector.Connector
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.util.ServerInfo
import org.apache.coyote.AbstractProtocol
import org.apache.tomcat.util.descriptor.web.{FilterDef, FilterMap}
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.server.{
  DefaultServiceErrorHandler,
  SSLClientAuthMode,
  Server,
  ServerBuilder,
  ServiceErrorHandler
}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.defaults
import org.http4s.servlet.{AsyncHttp4sServlet, ServletContainer, ServletIo}
import org.http4s.syntax.all._
import org.http4s.tomcat.server.TomcatBuilder._
import org.log4s.getLogger

import scala.collection.immutable
import scala.concurrent.duration._

sealed class TomcatBuilder[F[_]] private (
    socketAddress: InetSocketAddress,
    externalExecutor: Option[Executor],
    private val idleTimeout: Duration,
    private val asyncTimeout: Duration,
    private val servletIo: ServletIo[F],
    sslConfig: SslConfig,
    mounts: Vector[Mount[F]],
    private val serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String],
    classloader: Option[ClassLoader]
)(implicit protected val F: Async[F])
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
      sslConfig: SslConfig = sslConfig,
      mounts: Vector[Mount[F]] = mounts,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: immutable.Seq[String] = banner,
      classloader: Option[ClassLoader] = classloader
  ): Self =
    new TomcatBuilder(
      socketAddress,
      externalExecutor,
      idleTimeout,
      asyncTimeout,
      servletIo,
      sslConfig,
      mounts,
      serviceErrorHandler,
      banner,
      classloader
    )

  def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String = "TLS",
      trustStore: Option[StoreInfo] = None,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self =
    copy(
      sslConfig = new KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth))

  def withoutSsl: Self =
    copy(sslConfig = NoSsl)

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
    copy(mounts = mounts :+ Mount[F] { (ctx, index, _, _) =>
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
    copy(mounts = mounts :+ Mount[F] { (ctx, index, _, _) =>
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
    mountHttpApp(service.orNotFound, prefix)

  def mountHttpApp(service: HttpApp[F], prefix: String): Self =
    copy(mounts = mounts :+ Mount[F] { (ctx, index, builder, dispatcher) =>
      val servlet = new AsyncHttp4sServlet(
        service = service,
        asyncTimeout = builder.asyncTimeout,
        servletIo = builder.servletIo,
        serviceErrorHandler = builder.serviceErrorHandler,
        dispatcher = dispatcher
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

  def withClassloader(classloader: ClassLoader): Self =
    copy(classloader = Some(classloader))

  override def resource: Resource[F, Server] =
    Dispatcher[F].flatMap(dispatcher =>
      Resource(F.blocking {
        val tomcat = new Tomcat
        val cl = classloader.getOrElse(getClass.getClassLoader)
        val docBase = cl.getResource("") match {
          case null => null
          case resource => resource.getPath
        }
        tomcat.addContext("", docBase)

        val conn = tomcat.getConnector()
        sslConfig.configureConnector(conn)

        conn.setProperty("address", socketAddress.getHostString)
        conn.setPort(socketAddress.getPort)
        conn.setProperty(
          "connection_pool_timeout",
          (if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0).toString)

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
          mount.f(rootContext, i, this, dispatcher)

        tomcat.start()

        val server = new Server {
          lazy val address: InetSocketAddress = {
            val host = socketAddress.getHostString
            val port = tomcat.getConnector.getLocalPort
            new InetSocketAddress(host, port)
          }

          lazy val isSecure: Boolean = sslConfig.isSecure
        }

        val shutdown = F.blocking {
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
      }))
}

object TomcatBuilder {
  def apply[F[_]: Async]: TomcatBuilder[F] =
    new TomcatBuilder[F](
      socketAddress = defaults.IPv4SocketAddress,
      externalExecutor = None,
      idleTimeout = defaults.IdleTimeout,
      asyncTimeout = defaults.ResponseTimeout,
      servletIo = ServletContainer.DefaultServletIo[F],
      sslConfig = NoSsl,
      mounts = Vector.empty,
      serviceErrorHandler = DefaultServiceErrorHandler,
      banner = defaults.Banner,
      classloader = None
    )

  private sealed trait SslConfig {
    def configureConnector(conn: Connector): Unit
    def isSecure: Boolean
  }

  private class KeyStoreBits(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[StoreInfo],
      clientAuth: SSLClientAuthMode
  ) extends SslConfig {
    def configureConnector(conn: Connector) = {
      conn.setSecure(true)
      conn.setScheme("https")
      conn.setProperty("SSLEnabled", "true")

      // TODO These configuration properties are all deprecated
      conn.setProperty("keystoreFile", keyStore.path)
      conn.setProperty("keystorePass", keyStore.password)
      conn.setProperty("keyPass", keyManagerPassword)
      conn.setProperty(
        "clientAuth",
        clientAuth match {
          case SSLClientAuthMode.Required => "required"
          case SSLClientAuthMode.Requested => "optional"
          case SSLClientAuthMode.NotRequested => "none"
        }
      )
      conn.setProperty("sslProtocol", protocol)
      trustStore.foreach { ts =>
        conn.setProperty("truststoreFile", ts.path)
        conn.setProperty("truststorePass", ts.password)
      }
    }
    def isSecure = true
  }

  private object NoSsl extends SslConfig {
    def configureConnector(conn: Connector) = {
      val _ = conn
      ()
    }
    def isSecure = false
  }
}

private final case class Mount[F[_]](f: (Context, Int, TomcatBuilder[F], Dispatcher[F]) => Unit)
