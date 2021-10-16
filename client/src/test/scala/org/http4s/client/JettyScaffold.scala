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

package org.http4s.client

import cats.effect.concurrent.Ref
import cats.effect.{IO, Resource, Sync}
import cats.implicits._
import java.net.{InetSocketAddress}
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{Server => JServer, _}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty
import org.eclipse.jetty.io.EndPoint
import org.http4s.client.JettyScaffold._
import org.http4s.Uri

object JettyScaffold {
  def apply[F[_]](num: Int, secure: Boolean, testServlet: HttpServlet)(implicit
      F: Sync[F]): Resource[F, JettyScaffold] =
    Resource.make(F.delay {
      val scaffold = new JettyScaffold(num, secure)
      scaffold.startServers(testServlet)
    })(s => F.delay(s.stopServers()))

  class JettyTestServer(
      private[JettyScaffold] val server: JServer,
      val address: InetSocketAddress,
      val secure: Boolean,
      private val connectionsCounter: Ref[IO, Int]
  ) {
    def uri = Uri.unsafeFromString(
      s"${if (secure) "https" else "http"}://${address.getHostName}:${address.getPort}")

    def numberOfEstablishedConnections: IO[Int] = connectionsCounter.get

    def resetCounters(): IO[Unit] = connectionsCounter.set(0)
  }
}

class JettyScaffold private (num: Int, secure: Boolean) {

  var servers = Vector.empty[JettyTestServer]

  def startServers(testServlet: HttpServlet): this.type = {
    servers = (0 until num).map { _ =>
      val server = new JServer()
      val context = new ServletContextHandler()
      context.setContextPath("/")
      context.addServlet(new ServletHolder("Test-servlet", testServlet), "/*")

      server.setHandler(context)

      val connectionsCounter = Ref.unsafe[IO, Int](0)

      val connector =
        if (secure) {
          val ksStream = this.getClass.getResourceAsStream("/server.jks")
          val ks = KeyStore.getInstance("JKS")
          ks.load(ksStream, "password".toCharArray)
          ksStream.close()

          val kmf = KeyManagerFactory.getInstance(
            Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
              .getOrElse(KeyManagerFactory.getDefaultAlgorithm))

          kmf.init(ks, "secure".toCharArray)

          val sslContext = SSLContext.getInstance("TLS")
          sslContext.init(kmf.getKeyManagers, null, null)

          val sslContextFactory = new SslContextFactory.Server()
          sslContextFactory.setSslContext(sslContext)

          val httpsConfig = new HttpConfiguration()
          httpsConfig.setSecureScheme("https")
          httpsConfig.addCustomizer(new SecureRequestCustomizer())
          val connectionFactory = new CountingHttpConnectionFactory(httpsConfig, connectionsCounter)
          new ServerConnector(
            server,
            new SslConnectionFactory(
              sslContextFactory,
              org.eclipse.jetty.http.HttpVersion.HTTP_1_1.asString()),
            connectionFactory)
        } else {
          val httpConfig = new HttpConfiguration()
          val connectionFactory = new CountingHttpConnectionFactory(httpConfig, connectionsCounter)
          new ServerConnector(server, connectionFactory)
        }
      connector.setPort(0)
      server.addConnector(connector)
      server.start()

      val address = new InetSocketAddress(
        "localhost",
        server.getConnectors.head.asInstanceOf[ServerConnector].getLocalPort)

      new JettyTestServer(server, address, secure, connectionsCounter)
    }.toVector

    this
  }

  private class CountingHttpConnectionFactory(
      config: HttpConfiguration,
      connectionsCounter: Ref[IO, Int]
  ) extends HttpConnectionFactory(config) {
    override def newConnection(connector: Connector, endPoint: EndPoint): jetty.io.Connection = {
      connectionsCounter.update(_ + 1).unsafeRunSync()
      super.newConnection(connector, endPoint)
    }
  }

  def addresses: Vector[InetSocketAddress] = servers.map(_.address)

  def resetCounters(): IO[Unit] = servers.traverse[IO, Unit](_.resetCounters()).void

  def stopServers(): Unit = servers.foreach(_.server.stop())
}
