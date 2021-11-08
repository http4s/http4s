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

import cats.effect.Resource
import cats.effect.Sync
import org.eclipse.jetty.server.{Server => JServer, _}
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory

import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.servlet.http.HttpServlet

object JettyScaffold {
  def apply[F[_]](num: Int, secure: Boolean, testServlet: HttpServlet)(implicit
      F: Sync[F]
  ): Resource[F, JettyScaffold] =
    Resource.make(F.delay {
      val scaffold = new JettyScaffold(num, secure)
      scaffold.startServers(testServlet)
    })(s => F.delay(s.stopServers()))
}

class JettyScaffold private (num: Int, secure: Boolean) {
  private var servers = Vector.empty[JServer]
  var addresses = Vector.empty[InetSocketAddress]

  def startServers(testServlet: HttpServlet): this.type = {
    val res = (0 until num).map { _ =>
      val server = new JServer()
      val context = new ServletContextHandler()
      context.setContextPath("/")
      context.addServlet(new ServletHolder("Test-servlet", testServlet), "/*")

      server.setHandler(context)

      val connector =
        if (secure) {
          val ksStream = this.getClass.getResourceAsStream("/server.jks")
          val ks = KeyStore.getInstance("JKS")
          ks.load(ksStream, "password".toCharArray)
          ksStream.close()

          val kmf = KeyManagerFactory.getInstance(
            Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
              .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
          )

          kmf.init(ks, "secure".toCharArray)

          val sslContext = SSLContext.getInstance("TLS")
          sslContext.init(kmf.getKeyManagers, null, null)

          val sslContextFactory = new SslContextFactory.Server()
          sslContextFactory.setSslContext(sslContext)

          val httpsConfig = new HttpConfiguration()
          httpsConfig.setSecureScheme("https")
          httpsConfig.addCustomizer(new SecureRequestCustomizer())
          val connectionFactory = new HttpConnectionFactory(httpsConfig)
          new ServerConnector(
            server,
            new SslConnectionFactory(
              sslContextFactory,
              org.eclipse.jetty.http.HttpVersion.HTTP_1_1.asString(),
            ),
            connectionFactory,
          )
        } else new ServerConnector(server)
      connector.setPort(0)
      server.addConnector(connector)
      server.start()

      val address = new InetSocketAddress(
        "localhost",
        server.getConnectors.head.asInstanceOf[ServerConnector].getLocalPort,
      )

      (address, server)
    }.toVector

    servers = res.map(_._2)
    addresses = res.map(_._1)

    this
  }

  def stopServers(): Unit = servers.foreach(_.stop())
}
