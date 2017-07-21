package org.http4s
package client

import java.net.InetSocketAddress
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl._
import org.http4s.headers.{`Content-Length`, `Transfer-Encoding`}

import org.specs2.specification.core.Fragments

import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process


abstract class ClientRouteTestBattery(name: String, client: Client)
  extends Http4sSpec with JettyScaffold
{
  val timeout = 20.seconds

  Fragments.foreach(GetRoutes.getPaths.toSeq) { case (path, expected) =>
    s"Execute GET: $path" in {
      val name = address.getHostName
      val port = address.getPort
      val req = Request(uri = Uri.fromString(s"http://$name:$port$path").yolo)
      client.fetch(req) { resp =>
        Task.delay(checkResponse(resp, expected))
      }.runFor(timeout)
    }
  }

  name should {
    "Strip fragments from URI" in {
      skipped("Can only reproduce against external resource.  Help wanted.")
      val uri = Uri.uri("https://en.wikipedia.org/wiki/Buckethead_discography#Studio_albums")
      val body = client.fetch(Request(uri = uri)) {
        case resp => Task.now(resp.status)
      }
      body must returnValue(Ok)
    }

    "Repeat a simple request" in {
      val path = GetRoutes.SimplePath
      def fetchBody = client.toService(_.as[String]).local { uri: Uri =>
        Request(uri = uri)
      }
      val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo
      val f = (0 until 10).map(_ => Task.fork {
        val resp = fetchBody.run(url)
        resp.map(_.length)
      })
      foreach(Task.gatherUnordered(f).runFor(timeout)) { length =>
        length mustNotEqual 0
      }
    }

    "POST an empty body" in {
      val name = address.getHostName
      val port = address.getPort
      val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
      val req = POST(uri)
      val body = client.expect[String](req)
      body must returnValue("")
    }

    "POST a normal body" in {
      val name = address.getHostName
      val port = address.getPort
      val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
      val req = POST(uri, "This is normal.")
      val body = client.expect[String](req)
      body must returnValue("This is normal.")
    }

    "POST a chunked body" in {
      val name = address.getHostName
      val port = address.getPort
      val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
      val req = POST(uri, Process.eval(Task.now("This is chunked.")))
      val body = client.expect[String](req)
      body must returnValue("This is chunked.")
    }
  }

  override def map(fs: => Fragments) =
    super.map(fs ^ step(client.shutdown.run))

  def testServlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit = {
      GetRoutes.getPaths.get(req.getRequestURI) match {
        case Some(r) => renderResponse(srv, r)
        case None    => srv.sendError(404)
      }
    }

    override def doPost(req: HttpServletRequest, srv: HttpServletResponse): Unit = {
      srv.setStatus(200)
      val s = scala.io.Source.fromInputStream(req.getInputStream).mkString
      srv.getOutputStream.print(s)
      srv.getOutputStream.flush()
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

    val os = srv.getOutputStream
    resp.body.flatMap { body =>
      os.write(body.toArray)
      os.flush()
      Process.halt.asInstanceOf[Process[Task, Unit]]
    }.run.run
  }

  private def collectBody(body: EntityBody): Array[Byte] = body.runLog.run.toArray.map(_.toArray).flatten
}
