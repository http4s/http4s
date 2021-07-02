/*
 * Copyright 2021 http4s.org
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
package fetchclient

import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import java.util.Arrays
import java.util.Locale
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part

class FetchClientSuite extends Http4sSuite with Http4sClientDsl[IO] {

  val client = FetchClient[IO]

  def url(path: String): Uri =
    Uri.fromString(s"http://localhost:8888$path").yolo

  test("Repeat a simple request") {
    def fetchBody =
      client.toKleisli(_.as[String]).local { (uri: Uri) =>
        Request(uri = uri)
      }

    (0 until 10).toVector
      .parTraverse(_ => fetchBody.run(url("/simple")).map(_.length))
      .map(_.forall(_ =!= 0))
      .assert
  }

  test("POST an empty body") {
    val req = POST(url("/echo"))
    val body = client.expect[String](req)
    body.assertEquals("")
  }

  test("POST a normal body") {
    val req = POST("This is normal.", url("/echo"))
    val body = client.expect[String](req)
    body.assertEquals("This is normal.")
  }

  test("POST a chunked body") {
    val req = POST(Stream("This is chunked.").covary[IO], url("/echo"))
    val body = client.expect[String](req)
    body.assertEquals("This is chunked.")
  }

  test("POST a multipart body") {
    val multipart = Multipart[IO](Vector(Part.formData("text", "This is text.")))
    val req = POST(multipart, url("/echo")).withHeaders(multipart.headers)
    val body = client.expect[String](req)
    body.map(_.contains("This is text.")).assert
  }

  GetRoutes.getPaths.toList.foreach { case (path, expected) =>
    test(s"Execute GET $path") {
      val req = Request[IO](uri = url(path))
      client
        .run(req)
        .use(resp => expected.flatMap(checkResponse(resp, _)))
        .assert
    }
  }

  private def checkResponse(res: Response[IO], expected: Response[IO]): IO[Boolean] = {
    // This isn't a generically safe normalization for all header, but
    // it's close enough for our purposes
    def normalizeHeaderValue(h: Header.Raw) =
      Header.Raw(h.name, h.value.replace("; ", ";").toLowerCase(Locale.ROOT))

    for {
      _ <- IO(res.status).assertEquals(expected.status)
      body <- res.body.compile.to(Array)
      expBody <- expected.body.compile.to(Array)
      _ <- IO(body).map(Arrays.equals(_, expBody)).assert
      headers = res.headers.headers.map(normalizeHeaderValue)
      expectedHeaders = expected.headers.headers.map(normalizeHeaderValue)
      _ <- IO(expectedHeaders.diff(headers)).assertEquals(Nil)
      _ <- IO(res.httpVersion).assertEquals(expected.httpVersion)
    } yield true
  }
}
