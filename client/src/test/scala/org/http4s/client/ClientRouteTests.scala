package org.http4s
package client

import java.net.InetSocketAddress
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.eclipse.jetty.server.{Server => JServer, ServerConnector}
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}

import org.http4s.Uri.{Authority, RegName}

trait ClientRouteTests { self: Http4sSpec =>

  import org.http4s.Http4s._

  protected def name: String

  protected val client: Client

  // The main entry method for this
  protected def runTest(req: Request, address: InetSocketAddress): Response = {
    val newreq = req.copy(requestUri = req.requestUri.copy(authority = Some(Authority(host = RegName(address.getHostName),
                                                                                      port = Some(address.getPort)))))
    client.prepare(newreq).run
  }

  protected def runAllTests() {
    val address = new InetSocketAddress("localhost", 0)
    val server = getServer(address)
    val gets = translateTests(address.getPort, Method.GET, getPaths)

    gets.foreach{ case (req, resp) => internamRunTest(req, resp, address) }
  }

  protected def getServer(address: InetSocketAddress): JServer = {
    val server = new JServer()
    val context = new ServletContextHandler()
    context.setContextPath("/")
    server.setHandler(context)

    context.addServlet(new ServletHolder("Test servlet", testServlet), "/")

    val connector = new ServerConnector(server)
    connector.setPort(address.getPort)
    server.addConnector(connector)
    server
  }

  private val testServlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit = {
      getPaths.get(req.getRequestURI) match {
        case Some(r) => renderResponse(srv, r)
        case None    => srv.sendError(404)
      }
    }
  }

  private def internamRunTest(req: Request, expected: Response, address: InetSocketAddress): Unit = {
    s"Execute ${req.requestMethod}: ${req.requestUri}: " in {
      val received = runTest(req, address)
      checkResponse(received, expected)
    }
  }

  private def checkResponse(rec: Response, expected: Response) = {
    (rec.headers.toSet must be_==(expected.headers.toSet)) &&   // Have to do set to avoid order problems
      (rec.status must be_==(expected.status)) &&
      (rec.httpVersion must be_==(expected.httpVersion)) &&
      (collectBody(rec.body) must be_==(collectBody(expected.body)))
  }

  private def translateTests(port: Int, method: Method, paths: Map[String, Response]): Map[Request, Response] = {
    paths.map { case (s, r) =>
      (Request(method, requestUri = Uri.fromString(s"http://localhost:$port/$s").yolo), r)
    }
  }

  private def renderResponse(srv: HttpServletResponse, resp: Response): Unit = {
    srv.setStatus(resp.status.code)
    resp.headers.foreach(h =>srv.addHeader(h.name.toString, h.value))
  }

  private def collectBody(body: EntityBody): Array[Byte] = body.runLog.run.toArray.map(_.toArray).flatten

  /////////////// Define the routes here ////////////////////////////////

  protected val getPaths: Map[String, Response] = Map(
    "get" -> Ok("get").run
  )

}
