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
import org.http4s.client.scaffold.ServerScaffold
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part
import org.typelevel.ci._

import java.util.Arrays
import java.util.Locale
import scala.concurrent.duration._

abstract class ClientRouteTestBattery(name: String) extends Http4sSuite with Http4sClientDsl[IO] {
  val timeout: FiniteDuration = 20.seconds

  def clientResource: Resource[IO, Client[IO]]

  val testHandler: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ (Method.GET -> Root / "request-splitting") =>
      if (req.headers.get(ci"Evil").isDefined)
        InternalServerError()
      else
        Ok()
    case _ @(Method.GET -> path) =>
      GetRoutes.getPaths.get(path.toString).getOrElse(NotFound())
    case req @ (Method.POST -> _) =>
      Ok(req.body)
  }

  val serverClient = resourceSuiteDeferredFixture(
    "serverClient",
    ServerScaffold[IO](1, false, testHandler)
      .map(() => _)
      .product(clientResource.map(() => _)),
  )

  test(s"$name Repeat a simple request") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val path = GetRoutes.SimplePath

      def fetchBody =
        client().toKleisli(_.as[String]).local { (uri: Uri) =>
          Request(uri = uri)
        }

      val url = Uri.fromString(s"http://$address$path").yolo
      (0 until 10).toVector
        .parTraverse(_ => fetchBody.run(url).map(_.length))
        .map(_.forall(_ =!= 0))
        .assert
    }
  }

  test(s"$name POST an empty body") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val uri = Uri.fromString(s"http://$address/echo").yolo
      val req = POST(uri)
      val body = client().expect[String](req)
      body.assertEquals("")
    }
  }

  test(s"$name POST a normal body") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val uri = Uri.fromString(s"http://$address/echo").yolo
      val req = POST("This is normal.", uri)
      val body = client().expect[String](req)
      body.assertEquals("This is normal.")
    }
  }

  test(s"$name POST a chunked body".flaky) {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val uri = Uri.fromString(s"http://$address/echo").yolo
      val req = POST(Stream.emits("This is chunked.".toSeq.map(_.toString)).covary[IO], uri)
      val body = client().expect[String](req)
      body.assertEquals("This is chunked.")
    }
  }

  test(s"$name POST a multipart body") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val uri = Uri.fromString(s"http://$address/echo").yolo
      Multiparts
        .forSync[IO]
        .flatMap { multiparts =>
          multiparts.multipart(Vector(Part.formData("text", "This is text."))).flatMap {
            multipart =>
              val req = POST(multipart, uri).withHeaders(multipart.headers)
              val body = client().expect[String](req)
              body.map(_.contains("This is text."))
          }
        }
        .assert
    }
  }

  GetRoutes.getPaths.toList.foreach { case (path, expected) =>
    test(s"$name Execute GET $path") {
      serverClient().flatMap { case (server, client) =>
        val address = server().addresses.head
        val req = Request[IO](uri = Uri.fromString(s"http://$address$path").yolo)
        client()
          .run(req)
          .use(resp => expected.flatMap(checkResponse(resp, _)))
          .assert
      }
    }
  }

  test("Mitigates request splitting attack in URI path") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val name = address.host.toString
      val port = address.port.value
      val req = Request[IO](
        uri = Uri(
          authority = Uri.Authority(None, Uri.RegName(name), port = port.some).some,
          path = Uri.Path.Root / Uri.Path.Segment.encoded(
            "request-splitting HTTP/1.0\r\nEvil:true\r\nHide-Protocol-Version:"
          ),
        )
      )
      client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
    }
  }

  test("Mitigates request splitting attack in URI RegName") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val name = address.host
      val port = address.port.value
      val req = Request[IO](uri =
        Uri(
          authority =
            Uri.Authority(None, Uri.RegName(s"${name}\r\nEvil:true\r\n"), port = port.some).some,
          path = path"/request-splitting",
        )
      )
      client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
    }
  }

  test("Mitigates request splitting attack in field name") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val req = Request[IO](uri = Uri.fromString(s"http://$address/request-splitting").yolo)
        .putHeaders(Header.Raw(ci"Fine:\r\nEvil:true\r\n", "oops"))
      client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
    }
  }

  test("Mitigates request splitting attack in field value") {
    serverClient().flatMap { case (server, client) =>
      val address = server().addresses.head
      val req = Request[IO](uri = Uri.fromString(s"http://$address/request-splitting").yolo)
        .putHeaders(Header.Raw(ci"X-Carrier", "\r\nEvil:true\r\n"))
      client().status(req).handleError(_ => Status.Ok).assertEquals(Status.Ok)
    }
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
