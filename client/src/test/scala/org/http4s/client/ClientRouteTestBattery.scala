package org.http4s
package client

import java.net.InetSocketAddress
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.testroutes.GetRoutes
import org.specs2.specification.core.{Fragment, Fragments}
import scala.concurrent.duration.FiniteDuration
import fs2._

abstract class ClientRouteTestBattery(name: String, client: Client)
  extends JettyScaffold(name) with GetRoutes
{
  // Travis has been timing out intermittently.  Let's see if having All The Threads helps.
  sequential
  isolated

  override def cleanup() = {
    super.cleanup() // shuts down the jetty server
    client.shutdown.unsafeRun()
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
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit = {
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
    implicit val s = Strategy.sequential
    implicit val sched = Scheduler.fromFixedDaemonPool(1, "runTestThread")
    val newreq = req.copy(uri = req.uri.copy(authority = Some(Authority(host = RegName(address.getHostName),
      port = Some(address.getPort)))))
      timeout match {
        case finiteDuration: FiniteDuration =>
        client.fetch(newreq)(f).unsafeRunFor(finiteDuration)
        case _ =>
        client.fetch(newreq)(f).unsafeRun
      }

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

    val os : ServletOutputStream = srv.getOutputStream

    val writeBody : Task[Unit] = resp.body
      .evalMap{ byte => Task.delay(os.write(Array(byte))) }
      .run
    val flushOutputStream : Task[Unit] = Task.delay(os.flush())
    (writeBody >> flushOutputStream).unsafeRun()
  }

  private def collectBody(body: EntityBody): Array[Byte] = body.runLog.unsafeRun().toArray
}
