package org.http4s.client

import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{ServerConnector, Server => JServer}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}

class JettyScaffold(num: Int) {

  // hack to get a free port
  private def getNextPort() = {
    val socket = new ServerSocket(0)
    socket.setReuseAddress(true)
    val port = socket.getLocalPort()
    socket.close()
    port
  }

  private var servers = Vector.empty[JServer]
  var addresses = Vector.empty[InetSocketAddress]

  def startServers(testServlet: HttpServlet): Unit = {
    val res = (0 until num - 1).map { _ =>
      val address = new InetSocketAddress(InetAddress.getLocalHost(), getNextPort())
      val server = new JServer()
      val context = new ServletContextHandler()
      context.setContextPath("/")
      context.addServlet(new ServletHolder("Test-servlet", testServlet), "/*")

      server.setHandler(context)

      val connector = new ServerConnector(server)
      connector.setPort(address.getPort)

      server.addConnector(connector)
      server.start()

      (address, server)
    }.toVector

    servers = res.map(_._2)
    addresses = res.map(_._1)
  }

  def stopServers(): Unit = servers.foreach(_.stop())
}
