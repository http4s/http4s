package org.http4s.client

import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import javax.servlet.http.HttpServlet
import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.core.Fragments

trait JettyScaffold extends SpecificationLike {
  private val server = new JServer()
  var address: InetSocketAddress = null

  def testServlet: HttpServlet

  override def map(fs: => Fragments) =
    step(startServer()) ^ fs ^ step(server.stop())

  private def startServer(): InetSocketAddress = {
    address = new InetSocketAddress(InetAddress.getLocalHost(), JettyScaffold.getNextPort())

    val context = new ServletContextHandler()
    context.setContextPath("/")
    context.addServlet(new ServletHolder("Test-servlet", testServlet), "/*")

    server.setHandler(context)

    val connector = new ServerConnector(server)
    connector.setPort(address.getPort)

    server.addConnector(connector)
    server.start()

    address
  }
}

object JettyScaffold {

  // hack to get a free port
  private def getNextPort() = {
    val socket = new ServerSocket(0)
    socket.setReuseAddress(true)
    val port = socket.getLocalPort()
    socket.close()
    port
  }
}
