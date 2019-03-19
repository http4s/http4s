package org.http4s
package client

import cats.effect._
import cats.implicits._
import fs2._
import fs2.io._
import java.net.InetSocketAddress
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s.client.testroutes.GetRoutes
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.multipart.{Multipart, Part}
import org.specs2.specification.core.Fragments
import scala.concurrent.duration._

abstract class ClientRouteTestBattery(name: String) extends Http4sSpec with Http4sClientDsl[IO] {
  val timeout = 20.seconds
  var address: InetSocketAddress = null

  def clientResource: Resource[IO, Client[IO]]

  def testServlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit =
      GetRoutes.getPaths.get(req.getRequestURI) match {
        case Some(r) => renderResponse(srv, r)
        case None => srv.sendError(404)
      }

    override def doPost(req: HttpServletRequest, srv: HttpServletResponse): Unit = {
      srv.setStatus(200)
      val s = scala.io.Source.fromInputStream(req.getInputStream).mkString
      srv.getWriter.print(s)
      srv.getWriter.flush()
    }
  }

  withResource(JettyScaffold[IO](1, false, testServlet)) { jetty =>
    withResource(clientResource) { client =>
      val address = jetty.addresses.head

      Fragments.foreach(GetRoutes.getPaths.toSeq) {
        case (path, expected) =>
          s"Execute GET: $path" in {
            val name = address.getHostName
            val port = address.getPort
            val req = Request[IO](uri = Uri.fromString(s"http://$name:$port$path").yolo)
            client
              .fetch(req)(resp => checkResponse(resp, expected))
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

          def fetchBody = client.toKleisli(_.as[String]).local { uri: Uri =>
            Request(uri = uri)
          }

          val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo
          (0 until 10).toVector
            .parTraverse(_ => fetchBody.run(url).map(_.length))
            .unsafeRunTimed(timeout)
            .forall(_ mustNotEqual 0)
        }

        "POST an empty body" in {
          val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
          val req = POST(uri)
          val body = client.expect[String](req)
          body must returnValue("")
        }

        "POST a normal body" in {
          val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
          val req = POST("This is normal.", uri)
          val body = client.expect[String](req)
          body must returnValue("This is normal.")
        }

        "POST a chunked body" in {
          val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
          val req = POST(Stream("This is chunked.").covary[IO], uri)
          val body = client.expect[String](req)
          body must returnValue("This is chunked.")
        }

        "POST a multipart body" in {
          val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
          val multipart = Multipart[IO](Vector(Part.formData("text", "This is text.")))
          val req = POST(multipart, uri).map(_.withHeaders(multipart.headers))
          val body = client.expect[String](req)
          body must returnValue(containing("This is text."))
        }
      }
    }
  }

  private def checkResponse(rec: Response[IO], expected: Response[IO]): IO[Boolean] = {
    val hs = rec.headers.toSeq
    for {
      _ <- IO(rec.status must be_==(expected.status))
      body <- rec.body.compile.to[Array]
      expBody <- expected.body.compile.to[Array]
      _ <- IO(body must_== expBody)
      _ <- IO(expected.headers.foreach(h => h must beOneOf(hs: _*)))
      _ <- IO(rec.httpVersion must be_==(expected.httpVersion))
    } yield true
  }

  private def renderResponse(srv: HttpServletResponse, resp: Response[IO]): Unit = {
    srv.setStatus(resp.status.code)
    resp.headers.foreach { h =>
      srv.addHeader(h.name.toString, h.value)
    }
    resp.body
      .through(
        writeOutputStream[IO](
          IO.pure(srv.getOutputStream),
          testBlockingExecutionContext,
          closeAfterUse = false))
      .compile
      .drain
      .unsafeRunSync()
  }
}
