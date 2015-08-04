package org.http4s.client

import java.net.{ServerSocket, InetSocketAddress, InetAddress}
import javax.servlet.http.HttpServlet

import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.http4s.Http4sSpec
import org.specs2.specification.core.Fragments

import scala.concurrent.duration._


abstract class JettyScaffold(name: String) extends Http4sSpec {

  private val server = new JServer()

  def testServlet(): HttpServlet

  protected def runAllTests(): Fragments

  // Start the tests
  name >> {
    step(startup()) ^
      runAllTests()   ^
      step(cleanup())
  }

  def startup() = {}

  def cleanup() = {
    server.stop()
  }

  protected def timeout: Duration = 10.seconds

  protected def initializeServer(): InetSocketAddress = {
    val address = new InetSocketAddress(InetAddress.getLocalHost(), JettyScaffold.getNextPort())

    val context = new ServletContextHandler()
    context.setContextPath("/")
    context.addServlet(new ServletHolder("Test-servlet", testServlet()), "/*")

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

