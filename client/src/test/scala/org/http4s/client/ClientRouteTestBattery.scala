package org.http4s
package client

import java.net.{ServerSocket, InetSocketAddress}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}

import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.testroutes.GetRoutes

import org.specs2.specification.{ Fragment, Step }

import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process


abstract class ClientRouteTestBattery(name: String, client: Client)
  extends Http4sSpec with GetRoutes {

  protected def timeout: Duration = 10.seconds

  private val server = new JServer()

  // Start the tests
  name should {
    Step(startup()) ^
    runAllTests()   ^
    Step(cleanup())
  }

  def startup() = {}

  def cleanup() = {
    client.shutdown().run
    server.stop()
  }

  protected def runAllTests(): Seq[Fragment] = {
    val port = ClientRouteTestBattery.getNextPort()
    println(port)
    val address = new InetSocketAddress(port)
    initializeServer(address)
    val gets = translateTests(port, Method.GET, getPaths)
    println(gets)
    gets.map { case (req, resp) => runTest(req, resp, address) }.toSeq
  }

  protected def initializeServer(address: InetSocketAddress): Int = {
    val context = new ServletContextHandler()
    context.setContextPath("/")
    context.addServlet(new ServletHolder("Test-servlet", testServlet()), "/*")

    server.setHandler(context)

    val connector = new ServerConnector(server)
    connector.setPort(address.getPort)

    server.addConnector(connector)
    server.start()

    address.getPort
  }

  private def testServlet() = new HttpServlet {
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse) {
      getPaths.get(req.getRequestURI) match {
        case Some(r) => renderResponse(srv, r)
        case None    => srv.sendError(404)
      }
    }
  }

  private def runTest(req: Request, expected: Response, address: InetSocketAddress): Fragment = {
    println(s"Running $req")
    s"Execute ${req.method}: ${req.uri}" in {
      val received = runTest(req, address)
      checkResponse(received, expected)
    }
  }

  private def runTest(req: Request, address: InetSocketAddress): Response = {
    val newreq = req.copy(uri = req.uri.copy(authority = Some(Authority(host = RegName(address.getHostName),
      port = Some(address.getPort)))))
    client.prepare(newreq).runFor(timeout)
  }

  private def checkResponse(rec: Response, expected: Response) = {
    val hs = rec.headers.toSeq

    rec.status must be_==(expected.status)

    collectBody(rec.body) must be_==(collectBody(expected.body))

    expected.headers.foreach(h => h must beOneOf(hs:_*))

    rec.httpVersion must be_==(expected.httpVersion)
  }

  private def translateTests(port: Int, method: Method, paths: Map[String, Response]): Map[Request, Response] = {
    paths.map { case (s, r) =>
      (Request(method, uri = Uri.fromString(s"http://localhost:$port$s").yolo), r)
    }
  }

  private def renderResponse(srv: HttpServletResponse, resp: Response): Unit = {
    srv.setStatus(resp.status.code)
    resp.headers.foreach { h =>
      srv.addHeader(h.name.toString, h.value)
    }

    val os = srv.getOutputStream
    resp.body.flatMap { body =>
      os.write(body.toArray)
      os.flush()
      Process.halt.asInstanceOf[Process[Task, Unit]]
    }.run.run
  }

  private def collectBody(body: EntityBody): Array[Byte] = body.runLog.run.toArray.map(_.toArray).flatten
}

object ClientRouteTestBattery {

  // hack to get a free port
  private def getNextPort() = {
    val socket = new ServerSocket(0)
    socket.setReuseAddress(true)
    val port = socket.getLocalPort()
    socket.close()
    port
  }
}

