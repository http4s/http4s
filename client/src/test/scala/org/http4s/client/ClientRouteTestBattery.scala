package org.http4s
package client

import java.net.InetSocketAddress
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}


import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.testroutes.GetRoutes

import org.specs2.specification.core.{ Fragments, Fragment }

import scalaz.concurrent.Task
import scalaz.stream.Process


abstract class ClientRouteTestBattery(name: String, client: Client)
  extends JettyScaffold(name) with GetRoutes
{

  override def cleanup() = {
    client.shutdown.run
    super.cleanup()
  }

  override def runAllTests(): Fragments = {
    val address = initializeServer()
    val gets = translateTests(address, Method.GET, getPaths)
    val frags = gets.map { case (req, resp) => runTest(req, resp, address) }
                    .toSeq
                    .foldLeft(Fragments())(_ append _)

    frags
  }

  override def testServlet() = new HttpServlet {
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse) {
      getPaths.get(req.getRequestURI) match {
        case Some(r) => renderResponse(srv, r)
        case None    => srv.sendError(404)
      }
    }
  }

  private def runTest(req: Request, expected: Response, address: InetSocketAddress): Fragment = {
    s"Execute ${req.method}: ${req.uri}" in {
      runTest(req, address) { resp =>
        Task.delay(checkResponse(resp, expected))
      }
    }
  }

  private def runTest[A](req: Request, address: InetSocketAddress)(f: Response => Task[A]): A = {
    val newreq = req.copy(uri = req.uri.copy(authority = Some(Authority(host = RegName(address.getHostName),
      port = Some(address.getPort)))))
    client.fetch(newreq)(f).runFor(timeout)
  }

  private def checkResponse(rec: Response, expected: Response) = {
    val hs = rec.headers.toSeq

    rec.status must be_==(expected.status)

    collectBody(rec.body) must be_==(collectBody(expected.body))

    expected.headers.foreach(h => h must beOneOf(hs:_*))

    rec.httpVersion must be_==(expected.httpVersion)
  }

  private def translateTests(address: InetSocketAddress, method: Method, paths: Map[String, Response]): Map[Request, Response] = {
    val port = address.getPort()
    val name = address.getHostName()
    paths.map { case (s, r) =>
      (Request(method, uri = Uri.fromString(s"http://$name:$port$s").yolo), r)
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
