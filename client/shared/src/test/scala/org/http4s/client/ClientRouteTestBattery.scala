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
import java.util.Arrays
import java.util.Locale
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._
import org.http4s.multipart.{Multipart, Part}
import scala.concurrent.duration._

abstract class ClientRouteTestBattery(name: String) extends Http4sSuite with Http4sClientDsl[IO] {
  val timeout = 20.seconds

  def clientResource: Resource[IO, Client[IO]]

  def url(path: String): Uri =
    Uri.fromString(s"http://localhost:8888$path").yolo

  val client = resourceSuiteDeferredFixture("client", clientResource)

  test(s"$name Repeat a simple request") {
    val path = GetRoutes.SimplePath

    def fetchBody =
      client().map {
        _.toKleisli(_.as[String]).local { (uri: Uri) =>
          Request(uri = uri)
        }
      }

    fetchBody.flatMap { fetchBody =>
      (0 until 10).toVector
        .parTraverse(_ => fetchBody.run(url(path)).map(_.length))
        .map(_.forall(_ =!= 0))
        .assert
    }
  }

  test(s"$name POST an empty body") {
    val uri = url("/echo")
    val req = POST(uri)
    val body = client().flatMap(_.expect[String](req))
    body.assertEquals("")
  }

  test(s"$name POST a normal body") {
    val uri = url("/echo")
    val req = POST("This is normal.", uri)
    val body = client().flatMap(_.expect[String](req))
    body.assertEquals("This is normal.")
  }

  test(s"$name POST a chunked body".flaky) {
    val uri = url("/echo")
    val req = POST(Stream("This is chunked.").covary[IO], uri)
    val body = client().flatMap(_.expect[String](req))
    body.assertEquals("This is chunked.")
  }

  test(s"$name POST a multipart body") {
    val uri = url("/echo")
    val multipart = Multipart[IO](Vector(Part.formData("text", "This is text.")))
    val req = POST(multipart, uri).withHeaders(multipart.headers)
    val body = client().flatMap(_.expect[String](req))
    body.map(_.contains("This is text.")).assert
  }

  GetRoutes.getPaths.toList.foreach { case (path, expected) =>
    test(s"$name Execute GET $path") {
      val req = Request[IO](uri = url(path))
      Resource
        .eval(client())
        .flatMap(_.run(req))
        .use(resp => expected.flatMap(checkResponse(resp, _)))
        .assert
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
