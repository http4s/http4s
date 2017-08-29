package org.http4s
package client

import java.net.InetSocketAddress
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._
import org.http4s.headers.{`Content-Length`, `Transfer-Encoding`}

import org.specs2.specification.core.Fragments

import scala.concurrent.duration._

abstract class ClientRouteTestBattery(name: String, client: Client[IO])
  extends Http4sSpec with JettyScaffold {
  val timeout = 20.seconds

  Fragments.foreach(GetRoutes.getPaths.toSeq) { case (path, expected) =>
    s"Execute GET: $path" in {
      val name = address.getHostName
      val port = address.getPort
      val req = Request[IO](uri = Uri.fromString(s"http://$name:$port$path").yolo)
      client
        .fetch(req)(resp => IO(checkResponse(resp, expected)))
        .unsafeRunTimed(timeout)
        .get
    }
  }

  name should {
    "Strip fragments from URI" in {
      skipped("Can only reproduce against external resource.  Help wanted.")
      val uri = Uri.uri("https://en.wikipedia.org/wiki/Buckethead_discography#Studio_albums")
      val body = client.fetch(Request[IO](uri = uri))(e => IO.pure(e.status))
      body must returnValue(Ok)
    }

    "Repeat a simple request" in {
      val path = GetRoutes.SimplePath
      def fetchBody = client.toService(_.as[String]).local { uri: Uri =>
        Request(uri = uri)
      }
      val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo
      async.parallelTraverse((0 until 10).toVector)(_ =>
        fetchBody.run(url).map(_.length)
      ).unsafeRunTimed(timeout).forall(_ mustNotEqual 0)
    }

    "POST an empty body" in {
      val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
      val req = POST(uri)
      val body = client.expect[String](req)
      body must returnValue("")
    }

    "POST a normal body" in {
      val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
      val req = POST(uri, "This is normal.")
      val body = client.expect[String](req)
      body must returnValue("This is normal.")
    }

    "POST a chunked body" in {
      val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
      val req = POST(uri, Stream("This is chunked.").covary[IO])
      val body = client.expect[String](req)
      body must returnValue("This is chunked.")
    }
  }

  override def map(fs: => Fragments): Fragments =
    super.map(fs ^ step(client.shutdown.unsafeRunSync()))

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

  private def checkResponse(rec: Response[IO], expected: Response[IO]) = {
    val hs = rec.headers.toSeq

    rec.status must be_==(expected.status)

    collectBody(rec.body) must be_==(collectBody(expected.body))

    expected.headers.foreach(h => h must beOneOf(hs:_*))

    rec.httpVersion must be_==(expected.httpVersion)
  }

  private def translateTests(address: InetSocketAddress, method: Method, paths: Map[String, Response[IO]]): Map[Request[IO], Response[IO]] = {
    val port = address.getPort()
    val name = address.getHostName()
    paths.map { case (s, r) =>
      (Request[IO](method, uri = Uri.fromString(s"http://$name:$port$s").yolo), r)
    }
  }

  private def renderResponse(srv: HttpServletResponse, resp: Response[IO]): Unit = {
    srv.setStatus(resp.status.code)
    resp.headers.foreach { h =>
      srv.addHeader(h.name.toString, h.value)
    }

    val os : ServletOutputStream = srv.getOutputStream

    val writeBody : IO[Unit] = resp.body
      .evalMap{ byte => IO(os.write(Array(byte))) }
      .run
    val flushOutputStream : IO[Unit] = IO(os.flush())
    (writeBody >> flushOutputStream).unsafeRunSync()
  }

  private def collectBody(body: EntityBody[IO]): Array[Byte] = body.runLog.unsafeRunSync().toArray
}
