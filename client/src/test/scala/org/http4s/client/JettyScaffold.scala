package org.http4s.client

import cats.effect.{Resource, Sync}
import java.net.{InetAddress, InetSocketAddress}
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{Server => JServer, _}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.ssl.SslContextFactory

object JettyScaffold {
  def apply[F[_]](num: Int, secure: Boolean, testServlet: HttpServlet)(
      implicit F: Sync[F]): Resource[F, JettyScaffold] =
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
              .getOrElse(KeyManagerFactory.getDefaultAlgorithm))

          kmf.init(ks, "secure".toCharArray)

          val sslContext = SSLContext.getInstance("TLS")
          sslContext.init(kmf.getKeyManagers, null, null)

          val sslContextFactory = new SslContextFactory()
          sslContextFactory.setSslContext(sslContext)

          val httpsConfig = new HttpConfiguration()
          httpsConfig.setSecureScheme("https")
          httpsConfig.addCustomizer(new SecureRequestCustomizer())
          val connectionFactory = new HttpConnectionFactory(httpsConfig)
          new ServerConnector(
            server,
            new SslConnectionFactory(
              sslContextFactory,
              org.eclipse.jetty.http.HttpVersion.HTTP_1_1.asString()),
            connectionFactory)
        } else new ServerConnector(server)
      connector.setPort(0)
      server.addConnector(connector)
      server.start()

      val address = new InetSocketAddress(
        InetAddress.getLocalHost(),
        server.getConnectors.head.asInstanceOf[ServerConnector].getLocalPort)

      (address, server)
    }.toVector

    servers = res.map(_._2)
    addresses = res.map(_._1)

    this
  }

  def stopServers(): Unit = servers.foreach(_.stop())
}
