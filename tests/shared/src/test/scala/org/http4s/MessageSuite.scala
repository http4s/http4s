/*
 * Copyright 2013 http4s.org
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

import cats.data.Chain
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Pure
import org.http4s.headers.Authorization
import org.http4s.headers.Cookie
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`X-Forwarded-For`
import org.http4s.multipart.Boundary
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.http4s.syntax.all._
import org.typelevel.ci._
import org.typelevel.vault._

class MessageSuite extends Http4sSuite {
  private val local = SocketAddress(ipv4"127.0.0.1", port"8080")
  private val remote = SocketAddress(ipv4"192.168.0.1", port"45444")

  test("ConnectionInfo should get remote connection info when present") {
    val r = Request()
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.server, Some(local))
    assertEquals(r.remote, Some(remote))
  }

  test("ConnectionInfo should not contain remote connection info when not present") {
    val r = Request()
    assertEquals(r.server, None)
    assertEquals(r.remote, None)
  }

  test("ConnectionInfo should be utilized to determine the address of server and remote") {
    val r = Request()
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.serverAddr, Some(local.host))
    assertEquals(r.remoteAddr, Some(remote.host))
  }

  test("ConnectionInfo should be utilized to determine the port of server and remote") {
    val r = Request()
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.serverPort, Some(local.port))
    assertEquals(r.remotePort, Some(remote.port))
  }

  test(
    "ConnectionInfo should be utilized to determine the from value (first X-Forwarded-For if present)"
  ) {
    val forwardedValues =
      NonEmptyList.of(Some(ipv4"192.168.1.1"), Some(ipv4"192.168.1.2"))
    val r = Request()
      .withHeaders(Headers(`X-Forwarded-For`(forwardedValues)))
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.from, forwardedValues.head)
  }

  test(
    "ConnectionInfo should be utilized to determine the from value (remote value if X-Forwarded-For is not present)"
  ) {
    val r = Request()
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.from, Option(remote.host))
  }

  test("support cookies should contain a Cookie header when an explicit cookie is added") {
    assertEquals(
      Request(Method.GET)
        .addCookie(RequestCookie("token", "value"))
        .headers
        .get[Cookie]
        .map(_.value),
      Some("token=value"),
    )
  }

  test("support cookies should contain a Cookie header when multiple explicit cookies are added") {
    assertEquals(
      Request(Method.GET)
        .addCookie(RequestCookie("token1", "value1"))
        .addCookie(RequestCookie("token2", "value2"))
        .headers
        .get[Cookie]
        .map(_.value),
      Some("token1=value1; token2=value2"),
    )
  }

  test("support cookies should contain Cookie header(s) when a name/value pair is added") {
    assertEquals(
      Request(Method.GET)
        .addCookie("token", "value")
        .headers
        .get[Cookie]
        .map(_.value),
      Some("token=value"),
    )
  }

  test("support cookies should contain Cookie header(s) when name/value pairs are added") {
    assertEquals(
      Request(Method.GET)
        .addCookie("token1", "value1")
        .addCookie("token2", "value2")
        .headers
        .get[Cookie]
        .map(_.value),
      Some("token1=value1; token2=value2"),
    )
  }

  private val path1 = uri"/path1"
  private val path2 = path"/somethingelse"
  private val attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 3)

  test("Request.with...reset pathInfo if uri is changed") {
    val originalReq = Request(uri = path1, attributes = attributes)
    val updatedReq = originalReq.withUri(uri = Uri().withPath(path2))

    assertEquals(updatedReq.scriptName, Uri.Path.Root)
    assertEquals(updatedReq.pathInfo, path2)
  }

  test("Request.with... should not modify pathInfo if uri is unchanged") {
    val originalReq = Request(uri = path1, attributes = attributes)
    val updatedReq = originalReq.withMethod(method = Method.DELETE)

    assertEquals(originalReq.pathInfo, updatedReq.pathInfo)
    assertEquals(originalReq.scriptName, updatedReq.scriptName)
  }

  test("Request.with... should preserve caret in withPathInfo") {
    val originalReq =
      Request(uri = uri"/foo/bar", attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 1))
    val updatedReq = originalReq.withPathInfo(path"/quux")

    assertEquals(updatedReq.scriptName, path"/foo")
    assertEquals(updatedReq.pathInfo, path"/quux")
  }

  val cookieList = List(
    RequestCookie("test1", "value1"),
    RequestCookie("test2", "value2"),
    RequestCookie("test3", "value3"),
  )

  test("cookies should be empty if there are no Cookie headers present") {
    assertEquals(Request(Method.GET).cookies, List.empty)
  }

  test("cookies should parse discrete HTTP/1 Cookie header(s) into corresponding RequestCookies") {
    val cookies = "Cookie" -> "test1=value1; test2=value2; test3=value3"
    val request = Request(Method.GET, headers = Headers(cookies))
    assertEquals(request.cookies, cookieList)
  }

  test("cookies should parse discrete HTTP/2 Cookie header(s) into corresponding RequestCookies") {
    val cookies =
      Headers("Cookie" -> "test1=value1", "Cookie" -> "test2=value2", "Cookie" -> "test3=value3")
    val request = Request(Method.GET, headers = cookies)
    assertEquals(request.cookies, cookieList)
  }

  test(
    "cookies should parse HTTP/1 and HTTP/2 Cookie headers on a single request into corresponding RequestCookies"
  ) {
    val cookies = Headers(
      "Cookie" -> "test1=value1; test2=value2", // HTTP/1 style
      "Cookie" -> "test3=value3",
    ) // HTTP/2 style (separate headers for separate cookies)
    val request = Request(Method.GET, headers = cookies)
    assertEquals(request.cookies, cookieList)
  }

  test("toString should redact an Authorization header") {
    val request =
      Request[IO](Method.GET).putHeaders(Authorization(BasicCredentials("user", "pass")))
    assertEquals(
      request.toString,
      "Request(method=GET, uri=/, httpVersion=HTTP/1.1, headers=Headers(Authorization: <REDACTED>))",
    )
  }

  test("toString should redact Cookie Headers") {
    val request =
      Request[IO](Method.GET).addCookie("token", "value").addCookie("token2", "value2")
    assertEquals(
      request.toString,
      "Request(method=GET, uri=/, httpVersion=HTTP/1.1, headers=Headers(Cookie: <REDACTED>))",
    )
  }

  test("covary should disallow unrelated effects") {
    assert(
      compileErrors("Request[Option]().covary[IO]").nonEmpty
    )
  }

  test("covary should allow related effects") {
    trait F1[A]
    trait F2[A] extends F1[A]
    Request[F2]().covary[F1]
  }

  private val uri = uri"http://localhost:1234/foo"
  private val request = Request[IO](Method.GET, uri)

  test("asCurl should build cURL representation with scheme and authority") {
    val expected =
      """curl \
        |  --request GET \
        |  --url 'http://localhost:1234/foo'""".stripMargin

    assertEquals(request.asCurl(), expected)
  }

  test("asCurl should build cURL representation with headers") {
    val expected =
      """curl \
        |  --request GET \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'k1: v1' \
        |  --header 'k2: v2'""".stripMargin

    assertEquals(
      request
        .withHeaders("k1" -> "v1", "k2" -> "v2")
        .asCurl(),
      expected,
    )
  }

  test("asCurl should build cURL representation but redact sensitive information on default") {
    val expected =
      """curl \
        |  --request GET \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Cookie: <REDACTED>' \
        |  --header 'Authorization: <REDACTED>'""".stripMargin

    assertEquals(
      request
        .withHeaders("Cookie" -> "k3=v3; k4=v4", Authorization(BasicCredentials("user", "pass")))
        .asCurl(),
      expected,
    )
  }

  test("asCurl should build cURL representation but display sensitive headers on demand") {
    val expected =
      """curl \
        |  --request GET \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Cookie: k3=v3; k4=v4' \
        |  --header 'k5: v5' \
        |  --header 'Authorization: Basic dXNlcjpwYXNz'""".stripMargin

    assertEquals(
      request
        .withHeaders(
          "Cookie" -> "k3=v3; k4=v4",
          "k5" -> "v5",
          Authorization(BasicCredentials("user", "pass")),
        )
        .asCurl(_ => false),
      expected,
    )
  }

  test("asCurl should escape quotation marks in header") {
    val expected =
      """curl \
        |  --request GET \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'k6: '\''v6'\''' \
        |  --header ''\''k7'\'': v7'""".stripMargin

    assertEquals(
      request
        .withHeaders("k6" -> "'v6'", "'k7'" -> "v7")
        .asCurl(),
      expected,
    )
  }

  test("asCurlWithBody should build cURL representation with body") {
    val expected =
      """curl \
        |  --request POST \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Content-Length: 11' \
        |  --header 'Content-Type: text/plain; charset=UTF-8' \
        |  --data 'hello world'""".stripMargin

    assertIO(
      request
        .withMethod(Method.POST)
        .withEntity("hello world")
        .asCurlWithBody()
        ._1F,
      expected,
    )
  }

  test("asCurlWithBody should escape single quotes in body") {
    val expected =
      """curl \
        |  --request POST \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Content-Length: 11' \
        |  --header 'Content-Type: text/plain; charset=UTF-8' \
        |  --data 'hello y'\''all'""".stripMargin

    assertIO(
      request
        .withMethod(Method.POST)
        .withEntity("hello y'all")
        .asCurlWithBody()
        ._1F,
      expected,
    )
  }

  test("asCurlWithBody should escape single quotes in body") {
    val expected =
      """curl \
        |  --request POST \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Content-Length: 11' \
        |  --header 'Content-Type: text/plain; charset=UTF-8' \
        |  --data 'hello y'\''all'""".stripMargin

    assertIO(
      request
        .withMethod(Method.POST)
        .withEntity("hello y'all")
        .asCurlWithBody()
        ._1F,
      expected,
    )
  }

  test("asCurlWithBody should escape form data") {
    val expected =
      """curl \
        |  --request POST \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Content-Length: 64' \
        |  --header 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
        |  --data 'foo=simple&bar=one&bar=two&baz&qux=complex+%26+quoted+%3D+fun%21'""".stripMargin

    assertIO(
      request
        .withMethod(Method.POST)
        .withEntity(
          UrlForm(
            Map(
              "foo" -> Chain.one("simple"),
              "bar" -> Chain("one", "two"),
              "baz" -> Chain.nil,
              "qux" -> Chain.one("complex & quoted = fun!"),
            )
          )
        )
        .asCurlWithBody()
        ._1F,
      expected,
    )
  }

  test("asCurlWithBody should not mangle multi-part data") {
    val expected =
      """curl \
        |  --request POST \
        |  --url 'http://localhost:1234/foo' \
        |  --header 'Content-Type: multipart/form-data; boundary="1234567890"' \
        |  --data $'--1234567890\r
        |Content-Disposition: form-data; name="foo"\r
        |Content-Type: text/plain\r
        |\r
        |simple\r
        |--1234567890\r
        |Content-Disposition: form-data; name="bar"\r
        |\r
        |one\r
        |--1234567890\r
        |Content-Disposition: form-data; name="bar"\r
        |\r
        |two\r
        |--1234567890\r
        |Content-Disposition: form-data; name="baz"\r
        |\r
        |\r
        |--1234567890\r
        |Content-Disposition: form-data; name="qux"\r
        |\r
        |complex & quoted = fun!\r
        |--1234567890--\r
        |'""".stripMargin

    val multipart = Multipart[IO](
      Vector(
        Part.formData("foo", "simple", `Content-Type`(MediaType.text.`plain`)),
        Part.formData("bar", "one"),
        Part.formData("bar", "two"),
        Part.formData("baz", ""),
        Part.formData("qux", "complex & quoted = fun!"),
      ),
      Boundary("1234567890"),
    )

    assertIO(
      request
        .withMethod(Method.POST)
        .withEntity(multipart)
        .putHeaders(
          `Content-Type`(
            MediaType.multipartType(
              MediaType.multipart.`form-data`.subType,
              multipart.boundary.value.some,
            )
          )
        )
        .asCurlWithBody()
        ._1F,
      expected,
    )
  }

  test(
    "decode should produce a UnsupportedMediaType in the event of a decode failure MediaTypeMismatch"
  ) {
    val req =
      Request[IO](headers = Headers(`Content-Type`(MediaType.application.`octet-stream`)))
    val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
    resp.map(_.status).assertEquals(Status.UnsupportedMediaType)
  }

  test(
    "decode should produce a UnsupportedMediaType in the event of a decode failure MediaTypeMissing"
  ) {
    val req = Request[IO]()
    val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
    resp.map(_.status).assertEquals(Status.UnsupportedMediaType)
  }

  test("toString should redact a `Set-Cookie` header") {
    val resp = Response().putHeaders(headers.`Set-Cookie`(ResponseCookie("token", "value")))
    assertEquals(
      resp.toString,
      "Response(status=200, httpVersion=HTTP/1.1, headers=Headers(Set-Cookie: <REDACTED>))",
    )
  }

  test("not Found should return a plain text UTF-8 not found response") {
    val resp: Response[Pure] = Response.notFound

    assertEquals(resp.contentType, Some(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)))
    assertEquals(resp.status, Status.NotFound)
    assertEquals(resp.body.through(fs2.text.utf8.decode).toList.mkString(""), "Not found")
  }

  test("covary should disallow unrelated effects") {
    assert(
      compileErrors("Response[Option]().covary[IO]").nonEmpty
    )
  }

  test("covary should allow related effects") {
    trait F1[A]
    trait F2[A] extends F1[A]
    Response[F2]().covary[F1]
    true
  }

  test("withEntity should not duplicate Content-Type header") {
    // https://github.com/http4s/http4s/issues/5059
    val hdrs = Request[IO]()
      .putHeaders(`Content-Type`(MediaType.text.html))
      .withEntity[String]("foo")
      .headers
      .headers
      .filter(_.name === Header[`Content-Type`].name)
    // Should preserve only the EntityEncoder's Content-Type
    assertEquals(hdrs, List(Header.Raw(ci"Content-Type", "text/plain; charset=UTF-8")))
  }

  test("isIdempotent") {
    assert(Request[IO](method = Method.GET).isIdempotent)
    assert(Request[IO](method = Method.DELETE).isIdempotent)
    assert(Request[IO](method = Method.PUT).isIdempotent)
    assert(!Request[IO](method = Method.POST).isIdempotent)
    assert(
      Request[IO](method = Method.POST, headers = Headers("Idempotency-Key" -> "123")).isIdempotent
    )
  }
}
