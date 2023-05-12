/*
 * Copyright 2019 http4s.org
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
package ember.core

import cats.effect.Concurrent
import cats.effect.IO
import cats.syntax.all._
import fs2._
import org.http4s.syntax.literals._

class EncoderSuite extends Http4sSuite {
  private object Helpers {
    def stripLines(s: String): String = s.replace("\r\n", "\n")

    // Only for Use with Text Requests
    def encodeRequestRig[F[_]: Concurrent](req: Request[F]): F[String] =
      Encoder
        .reqToBytes(req)
        .through(fs2.text.utf8.decode[F])
        .compile
        .string
        .map(stripLines)

    // Only for Use with Text Requests
    def encodeResponseRig[F[_]: Concurrent](resp: Response[F]): F[String] =
      Encoder
        .respToBytes(resp)
        .through(fs2.text.utf8.decode[F])
        .compile
        .string
        .map(stripLines)
  }

  test("reqToBytes should encode a no body GET request correctly") {
    val req = Request[IO](Method.GET, uri"http://www.google.com")
    val expected =
      """GET / HTTP/1.1
      |Host: www.google.com
      |
      |""".stripMargin

    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("reqToBytes should encode a request with a body correctly") {
    val req = Request[IO](Method.POST, uri"http://www.google.com")
      .withEntity("Hello World!")
    val expected =
      """POST / HTTP/1.1
      |Host: www.google.com
      |Content-Length: 12
      |Content-Type: text/plain; charset=UTF-8
      |
      |Hello World!""".stripMargin

    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("reqToBytes should encode headers correctly") {
    val req = Request[IO](
      Method.GET,
      uri"http://www.google.com",
      headers = Headers("foo" -> "bar"),
    )
    val expected =
      """GET / HTTP/1.1
        |Host: www.google.com
        |foo: bar
        |
        |""".stripMargin
    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("reqToBytes strips the fragment") {
    val req = Request[IO](
      Method.GET,
      uri"https://www.example.com/path?query#fragment",
    )
    val expected =
      """GET /path?query HTTP/1.1
        |Host: www.example.com
        |
        |""".stripMargin
    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("reqToBytes respects the host header") {
    val req = Request[IO](
      Method.GET,
      uri"https://www.example.com/",
      headers = Headers(headers.Host("example.org", Some(8080))),
    )
    val expected =
      """GET / HTTP/1.1
        |Host: example.org:8080
        |
        |""".stripMargin
    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("reqToBytes should encode a no body POST request correctly") {
    val req = Request[IO](Method.POST)

    val expected =
      """POST / HTTP/1.1
      |Content-Length: 0
      |
      |""".stripMargin

    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("respToBytes should encode a no body response correctly") {
    val resp = Response[IO](Status.Ok)

    val expected =
      """HTTP/1.1 200 OK
      |Content-Length: 0
      |
      |""".stripMargin

    Helpers.encodeResponseRig(resp).assertEquals(expected)
  }

  test("respToBytes should encode a no body response correctly with no header") {
    val resp = Response[IO](Status.Ok)

    val expected =
      """HTTP/1.1 200 OK
      |Content-Length: 0
      |
      |""".stripMargin

    Helpers.encodeResponseRig(resp).assertEquals(expected)
  }

  test("reqToBytes should encode a no body request correctly with stream") {
    val req = Request[IO](Method.POST, body = Stream.chunk(Chunk.empty))

    val expected =
      """POST / HTTP/1.1
      |Transfer-Encoding: chunked
      |
      |0
      |
      |""".stripMargin

    Helpers.encodeRequestRig(req).assertEquals(expected)
  }

  test("respToBytes should encode a no body response correctly with stream") {
    val resp = Response[IO](Status.Ok, body = Stream.chunk(Chunk.empty))

    val expected =
      """HTTP/1.1 200 OK
      |Transfer-Encoding: chunked
      |
      |0
      |
      |""".stripMargin

    Helpers.encodeResponseRig(resp).assertEquals(expected)
  }

  test("respToBytes should encode trailer headers") {
    val resp = Response[IO](Status.Ok)
      .withEntity(Stream[IO, String]("Mozilla", "Developer", "Network"))
      .putHeaders("Trailer" -> "Expires")
      .withTrailerHeaders(IO(Headers("Expires" -> "Wed, 21 Oct 2015 07:28:00 GMT")))

    val expected =
      """HTTP/1.1 200 OK
      |Content-Type: text/plain; charset=UTF-8
      |Transfer-Encoding: chunked
      |Trailer: Expires
      |
      |7
      |Mozilla
      |9
      |Developer
      |7
      |Network
      |0
      |Expires: Wed, 21 Oct 2015 07:28:00 GMT
      |
      |""".stripMargin

    Helpers.encodeResponseRig(resp).assertEquals(expected)
  }

  test("encoder a response where entity is not allowed correctly") {
    val resp = Response[IO](Status.NoContent)
    val expected =
      """HTTP/1.1 204 No Content
      |
      |""".stripMargin

    Helpers.encodeResponseRig(resp).assertEquals(expected)
  }

  test("encode a response with a body correctly") {
    val resp = Response[IO](Status.NotFound)
      .withEntity("Not Found")

    val expected =
      """HTTP/1.1 404 Not Found
      |Content-Length: 9
      |Content-Type: text/plain; charset=UTF-8
      |
      |Not Found""".stripMargin

    Helpers.encodeResponseRig(resp).assertEquals(expected)
  }
}
