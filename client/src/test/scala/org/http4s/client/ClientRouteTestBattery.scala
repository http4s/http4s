/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client

import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.io._
import java.util.Locale
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s.client.testroutes.GetRoutes
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.multipart.{Multipart, Part}
import scala.concurrent.duration._
import java.util.Arrays

abstract class ClientRouteTestBattery(name: String) extends Http4sSuite with Http4sClientDsl[IO] {
  val timeout = 20.seconds

  def clientResource: Resource[IO, Client[IO]]

  def testServlet =
    new HttpServlet {
      override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit =
        req.getPathInfo match {
          case "/request-splitting" =>
            Option(req.getHeader("Evil")) match {
              case None => srv.setStatus(200)
              case Some(_) => srv.sendError(500)
            }
          case _ =>
            GetRoutes.getPaths.get(req.getRequestURI) match {
              case Some(r) =>
                renderResponse(srv, r.unsafeRunSync())
                  .unsafeRunSync() // We are outside the IO world
              case None => srv.sendError(404)
            }
        }

      override def doPost(req: HttpServletRequest, srv: HttpServletResponse): Unit = {
        srv.setStatus(200)
        val s = scala.io.Source.fromInputStream(req.getInputStream).mkString
        srv.getWriter.print(s)
        srv.getWriter.flush()
      }
    }

  // Need to override the context shift from munitCatsEffect
  // This is only required for JettyClient
  implicit val contextShift: ContextShift[IO] = Http4sSuite.TestContextShift

  val jetty = resourceSuiteFixture("server", JettyScaffold[IO](1, false, testServlet))
  val client = resourceSuiteFixture("client", clientResource)

  test(s"$name Repeat a simple request") {
    val address = jetty().addresses.head
    val path = GetRoutes.SimplePath

    def fetchBody =
      client().toKleisli(_.as[String]).local { (uri: Uri) =>
        Request(uri = uri)
      }

    val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo
    (0 until 10).toVector
      .parTraverse(_ => fetchBody.run(url).map(_.length))
      .map(_.forall(_ =!= 0))
      .assert
  }

  test(s"$name POST an empty body") {
    val address = jetty().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val req = POST(uri)
    val body = client().expect[String](req)
    body.assertEquals("")
  }

  test(s"$name POST a normal body") {
    val address = jetty().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val req = POST("This is normal.", uri)
    val body = client().expect[String](req)
    body.assertEquals("This is normal.")
  }

  test(s"$name POST a chunked body".flaky) {
    val address = jetty().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val req = POST(Stream("This is chunked.").covary[IO], uri)
    val body = client().expect[String](req)
    body.assertEquals("This is chunked.")
  }

  test(s"$name POST a multipart body") {
    val address = jetty().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val multipart = Multipart[IO](Vector(Part.formData("text", "This is text.")))
    val req = POST(multipart, uri).withHeaders(multipart.headers)
    val body = client().expect[String](req)
    body.map(_.contains("This is text.")).assert
  }

  test(s"$name Execute GET") {
    val address = jetty().addresses.head
    GetRoutes.getPaths.toList.traverse { case (path, expected) =>
      val name = address.getHostName
      val port = address.getPort
      val req = Request[IO](uri = Uri.fromString(s"http://$name:$port$path").yolo)
      client()
        .run(req)
        .use(resp => expected.flatMap(expectedResponse => checkResponse(resp, expectedResponse)))
        .assert
    }
  }

  test("Mitigates request splitting attack in URI path") {
    val address = jetty().addresses.head
    val name = address.getHostName
    val port = address.getPort
    val req = Request[IO](
      uri = Uri(
        authority = Uri.Authority(None, Uri.RegName(name), port = port.some).some,
        path = Uri.Path.Root / Uri.Path.Segment.encoded(
          "request-splitting HTTP/1.0\r\nEvil:true\r\nHide-Protocol-Version:")
      ))
    client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
  }

  test("Mitigates request splitting attack in URI RegName") {
    val address = jetty().addresses.head
    val name = address.getHostName
    val port = address.getPort
    val req = Request[IO](uri = Uri(
      authority =
        Uri.Authority(None, Uri.RegName(s"${name}\r\nEvil:true\r\n"), port = port.some).some,
      path = path"/request-splitting"))
    client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
  }

  test("Mitigates request splitting attack in field name") {
    val address = jetty().addresses.head
    val req = Request[IO](
      uri =
        Uri.fromString(s"http://${address.getHostName}:${address.getPort}/request-splitting").yolo)
      .putHeaders(Header.Raw("Fine:\r\nEvil:true\r\n".ci, "oops"))
    client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
  }

  test("Mitigates request splitting attack in field value") {
    val address = jetty().addresses.head
    val req = Request[IO](
      uri =
        Uri.fromString(s"http://${address.getHostName}:${address.getPort}/request-splitting").yolo)
      .putHeaders(Header.Raw("X-Carrier".ci, "\r\nEvil:true\r\n"))
    client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
  }

  private def checkResponse(rec: Response[IO], expected: Response[IO]): IO[Boolean] = {
    // This isn't a generically safe normalization for all header, but
    // it's close enough for our purposes
    def normalizeHeaderValue(h: Header.Raw) =
      Header.Raw(h.name, h.value.replace("; ", ";").toLowerCase(Locale.ROOT))

    for {
      _ <- IO(rec.status).assertEquals(expected.status)
      body <- rec.body.compile.to(Array)
      expBody <- expected.body.compile.to(Array)
      _ <- IO(body).map(Arrays.equals(_, expBody)).assert
      headers = rec.headers.headers.map(normalizeHeaderValue)
      expectedHeaders = expected.headers.headers.map(normalizeHeaderValue)
      _ <- IO(expectedHeaders.diff(headers)).assertEquals(Nil)
      _ <- IO(rec.httpVersion).assertEquals(expected.httpVersion)
    } yield true
  }

  private def renderResponse(srv: HttpServletResponse, resp: Response[IO]): IO[Unit] = {
    srv.setStatus(resp.status.code)
    resp.headers.foreach { h =>
      srv.addHeader(h.name.toString, h.value)
    }
    resp.body
      .through(
        writeOutputStream[IO](IO.pure(srv.getOutputStream), testBlocker, closeAfterUse = false))
      .compile
      .drain
  }
}
