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
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.typelevel.ci._

import java.util.Arrays
import java.util.Locale
import scala.concurrent.duration._

abstract class ClientRouteTestBattery(name: String)
    extends Http4sSuite
    with Http4sClientDsl[IO]
    with ClientRouteTestBatteryPlatform {
  val timeout = 20.seconds

  def clientResource: Resource[IO, Client[IO]]

  def url(path: String): IO[Uri] =
    server().map(s => Uri.fromString(s"http://${s.address.toString}$path").yolo)

  val server = resourceSuiteDeferredFixture("server", serverResource)
  val client = resourceSuiteDeferredFixture("client", clientResource)

  test(s"$name Repeat a simple request") {
    val path = GetRoutes.SimplePath

    def fetchBody =
      client().map {
        _.toKleisli(_.as[String]).local { (uri: Uri) =>
          Request(uri = uri)
        }
      }

    for {
      fetchBody <- fetchBody
      url <- url(path)
    } yield (0 until 10).toVector
      .parTraverse(_ => fetchBody.run(url).map(_.length))
      .map(_.forall(_ =!= 0))
      .assert
  }

  test(s"$name POST an empty body") {
    for {
      uri <- url("/echo")
      req = POST(uri)
      body = client().flatMap(_.expect[String](req))
      _ <- body.assertEquals("")
    } yield ()
  }

  test(s"$name POST a normal body") {
    for {
      uri <- url("/echo")
      req = POST("This is normal.", uri)
      body = client().flatMap(_.expect[String](req))
      _ <- body.assertEquals("This is normal.")
    } yield ()
  }

  test(s"$name POST a chunked body".flaky) {
    for {
      uri <- url("/echo")
      req = POST(Stream("This is chunked.").covary[IO], uri)
      body = client().flatMap(_.expect[String](req))
      _ <- body.assertEquals("This is chunked.")
    } yield ()
  }

  test(s"$name POST a multipart body") {
    for {
      uri <- url("/echo")
      multipart = Multipart[IO](Vector(Part.formData("text", "This is text.")))
      req = POST(multipart, uri).withHeaders(multipart.headers)
      body = client().flatMap(_.expect[String](req))
      _ <- body.map(_.contains("This is text.")).assert
    } yield ()
  }

  GetRoutes.getPaths.toList.foreach { case (path, expected) =>
    test(s"$name Execute GET $path") {
      (for {
        client <- Resource.eval(client())
        uri <- Resource.eval(url(path))
        req = Request[IO](uri = uri)
        resp <- client.run(req)
      } yield resp).use(resp => expected.flatMap(checkResponse(resp, _))).assert
    }
  }

  test("Mitigates request splitting attack in URI path") {
    for {
      uri <- url("/").map(
        _.withPath(Uri.Path(Vector(Uri.Path.Segment.encoded(
          "request-splitting HTTP/1.0\r\nEvil:true\r\nHide-Protocol-Version:")))))
      req = Request[IO](uri = uri)
      c <- client()
      status <- c.status(req).handleError(_ => Status.Ok)
    } yield assertEquals(status, Status.Ok)
  }

  test("Mitigates request splitting attack in URI RegName") {
    for {
      srv <- server()
      address = srv.address
      hostname = address.host.toString
      port = address.port.value
      req = Request[IO](
        uri = Uri(
          authority = Uri
            .Authority(None, Uri.RegName(s"${hostname}\r\nEvil:true\r\n"), port = port.some)
            .some,
          path = path"/request-splitting"))
      c <- client()
      status <- c.status(req).handleError(_ => Status.Ok)
    } yield assertEquals(status, Status.Ok)
  }

  test("Mitigates request splitting attack in field name") {

    for {
      srv <- server()
      uri <- url("/request-splitting")
      req = Request[IO](uri = uri)
        .putHeaders(Header.Raw(ci"Fine:\r\nEvil:true\r\n", "oops"))
      c <- client()
      status <- c.status(req).handleError(_ => Status.Ok)
    } yield assertEquals(status, Status.Ok)
  }

  test("Mitigates request splitting attack in field value") {
    for {
      uri <- url("/request-splitting")
      req = Request[IO](uri = uri)
        .putHeaders(Header.Raw(ci"X-Carrier", "\r\nEvil:true\r\n"))
      c <- client()
      status <- c.status(req).handleError(_ => Status.Ok)
    } yield assertEquals(status, Status.Ok)
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
}

object ClientRouteTestBattery {
  val App: HttpApp[IO] = HttpApp[IO] { request =>
    println(request)
    val get = Some(request).filter(_.method == Method.GET).flatMap { r =>
      r.uri.path.toString match {
        case p if p.startsWith("/request-splitting") =>
          if (r.headers.get(ci"Evil").isDefined) IO(Response[IO](Status.InternalServerError)).some
          else IO(Response[IO](Status.Ok)).some
        case p =>
          GetRoutes.getPaths.get(r.uri.path.toString)
      }
    }

    val post = Some(request).filter(_.method == Method.POST).map { r =>
      IO(Response(body = r.body))
    }

    get.orElse(post).getOrElse(IO(Response[IO](status = Status.NotFound)))
  }
}
