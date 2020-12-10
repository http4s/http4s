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

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Pure
import java.net.{InetAddress, InetSocketAddress}

import org.http4s.headers.{Authorization, `Content-Type`, `X-Forwarded-For`}
import org.http4s.syntax.all._
import _root_.io.chrisdavenport.vault._
import org.http4s.Uri.{Authority, Scheme}

class MessageSuite extends Http4sSuite {
  val local = InetSocketAddress.createUnresolved("www.local.com", 8080)
  val remote = InetSocketAddress.createUnresolved("www.remote.com", 45444)

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
    assertEquals(r.serverAddr, local.getHostString)
    assertEquals(r.remoteAddr, Some(remote.getHostString))
  }

  test("ConnectionInfo should be utilized to determine the port of server and remote") {
    val r = Request()
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.serverPort, local.getPort)
    assertEquals(r.remotePort, Some(remote.getPort))
  }

  test(
    "ConnectionInfo should be utilized to determine the from value (first X-Forwarded-For if present)") {
    val forwardedValues =
      NonEmptyList.of(Some(InetAddress.getLocalHost), Some(InetAddress.getLoopbackAddress))
    val r = Request()
      .withHeaders(Headers.of(`X-Forwarded-For`(forwardedValues)))
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.from, forwardedValues.head)
  }

  test(
    "ConnectionInfo should be utilized to determine the from value (remote value if X-Forwarded-For is not present)") {
    val r = Request()
      .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
    assertEquals(r.from, Option(remote.getAddress))
  }

  test("support cookies should contain a Cookie header when an explicit cookie is added") {
    assertEquals(
      Request(Method.GET)
        .addCookie(RequestCookie("token", "value"))
        .headers
        .get("Cookie".ci)
        .map(_.value),
      Some("token=value"))
  }

  test(
    "support cookies should contain a single Cookie header when multiple explicit cookies are added") {
    assertEquals(
      Request(Method.GET)
        .addCookie(RequestCookie("token1", "value1"))
        .addCookie(RequestCookie("token2", "value2"))
        .headers
        .get("Cookie".ci)
        .map(_.value),
      Some("token1=value1; token2=value2")
    )
  }

  test("support cookies should contain a Cookie header when a name/value pair is added") {
    assertEquals(
      Request(Method.GET)
        .addCookie("token", "value")
        .headers
        .get("Cookie".ci)
        .map(_.value),
      Some("token=value"))
  }

  test("support cookies should contain a single Cookie header when name/value pairs are added") {
    assertEquals(
      Request(Method.GET)
        .addCookie("token1", "value1")
        .addCookie("token2", "value2")
        .headers
        .get("Cookie".ci)
        .map(_.value),
      Some("token1=value1; token2=value2")
    )
  }

  val path1 = "/path1"
  val path2 = "/somethingelse"
  val attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 3)

  test("Request.with...reset pathInfo if uri is changed") {
    val originalReq = Request(uri = Uri(path = path1), attributes = attributes)
    val updatedReq = originalReq.withUri(uri = Uri(path = path2))

    assertEquals(updatedReq.scriptName, "")
    assertEquals(updatedReq.pathInfo, path2)
  }

  test("Request.with... should not modify pathInfo if uri is unchanged") {
    val originalReq = Request(uri = Uri(path = path1), attributes = attributes)
    val updatedReq = originalReq.withMethod(method = Method.DELETE)

    assertEquals(originalReq.pathInfo, updatedReq.pathInfo)
    assertEquals(originalReq.scriptName, updatedReq.scriptName)
  }

  test("Request.with... should preserve caret in withPathInfo") {
    val originalReq = Request(
      uri = Uri(path = "/foo/bar"),
      attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 4))
    val updatedReq = originalReq.withPathInfo("/quux")

    assertEquals(updatedReq.scriptName, "/foo")
    assertEquals(updatedReq.pathInfo, "/quux")
  }

  val cookieList = List(
    RequestCookie("test1", "value1"),
    RequestCookie("test2", "value2"),
    RequestCookie("test3", "value3"))

  test("cookies should be empty if there are no Cookie headers present") {
    assertEquals(Request(Method.GET).cookies, List.empty)
  }

  test("cookies should parse discrete HTTP/1 Cookie header(s) into corresponding RequestCookies") {
    val cookies = Header("Cookie", "test1=value1; test2=value2; test3=value3")
    val request = Request(Method.GET, headers = Headers.of(cookies))
    assertEquals(request.cookies, cookieList)
  }

  test("cookies should parse discrete HTTP/2 Cookie header(s) into corresponding RequestCookies") {
    val cookies = Headers.of(
      Header("Cookie", "test1=value1"),
      Header("Cookie", "test2=value2"),
      Header("Cookie", "test3=value3"))
    val request = Request(Method.GET, headers = cookies)
    assertEquals(request.cookies, cookieList)
  }

  test(
    "cookies should parse HTTP/1 and HTTP/2 Cookie headers on a single request into corresponding RequestCookies") {
    val cookies = Headers.of(
      Header("Cookie", "test1=value1; test2=value2"), // HTTP/1 style
      Header("Cookie", "test3=value3")
    ) // HTTP/2 style (separate headers for separate cookies)
    val request = Request(Method.GET, headers = cookies)
    assertEquals(request.cookies, cookieList)
  }

  test("toString should redact an Authorization header") {
    val request =
      Request[IO](Method.GET).putHeaders(Authorization(BasicCredentials("user", "pass")))
    assertEquals(
      request.toString,
      "Request(method=GET, uri=/, headers=Headers(Authorization: <REDACTED>))")
  }

  test("toString should redact Cookie Headers") {
    val request =
      Request[IO](Method.GET).addCookie("token", "value").addCookie("token2", "value2")
    assertEquals(
      request.toString,
      "Request(method=GET, uri=/, headers=Headers(Cookie: <REDACTED>))")
  }

  test("covary should disallow unrelated effects") {
    illTyped("Request[Option]().covary[IO]")
  }

  test("covary should allow related effects") {
    trait F1[A]
    trait F2[A] extends F1[A]
    Request[F2]().covary[F1]
  }

  val port = 1234
  val uri = Uri(
    path = "/foo",
    scheme = Some(Scheme.http),
    authority = Some(Authority(port = Some(port)))
  )
  val request = Request[IO](Method.GET, uri)

  test("asCurl should build cURL representation with scheme and authority") {
    assertEquals(request.asCurl(), "curl -X GET 'http://localhost:1234/foo'")
  }

  test("asCurl should build cURL representation with headers") {
    assertEquals(
      request
        .withHeaders(Header("k1", "v1"), Header("k2", "v2"))
        .asCurl(),
      "curl -X GET 'http://localhost:1234/foo' -H 'k1: v1' -H 'k2: v2'")
  }

  test("asCurl should build cURL representation but redact sensitive information on default") {
    assertEquals(
      request
        .withHeaders(
          Header("Cookie", "k3=v3; k4=v4"),
          Authorization(BasicCredentials("user", "pass")))
        .asCurl(),
      "curl -X GET 'http://localhost:1234/foo' -H 'Cookie: <REDACTED>' -H 'Authorization: <REDACTED>'"
    )
  }

  test("asCurl should build cURL representation but display sensitive headers on demand") {
    assertEquals(
      request
        .withHeaders(
          Header("Cookie", "k3=v3; k4=v4"),
          Header("k5", "v5"),
          Authorization(BasicCredentials("user", "pass")))
        .asCurl(_ => false),
      "curl -X GET 'http://localhost:1234/foo' -H 'Cookie: k3=v3; k4=v4' -H 'k5: v5' -H 'Authorization: Basic dXNlcjpwYXNz'"
    )
  }

  test("asCurl should escape quotation marks in header") {
    assertEquals(
      request
        .withHeaders(Header("k6", "'v6'"), Header("'k7'", "v7"))
        .asCurl(),
      s"""curl -X GET 'http://localhost:1234/foo' -H 'k6: '\\''v6'\\''' -H ''\\''k7'\\'': v7'"""
    )
  }

  test(
    "decode should produce a UnsupportedMediaType) the event of a decode failure MediaTypeMismatch") {
    val req =
      Request[IO](headers = Headers.of(`Content-Type`(MediaType.application.`octet-stream`)))
    val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
    resp.map(_.status).assertEquals(Status.UnsupportedMediaType)
  }

  test(
    "decode should produce a UnsupportedMediaType) the event of a decode failure MediaTypeMissing") {
    val req = Request[IO]()
    val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
    resp.map(_.status).assertEquals(Status.UnsupportedMediaType)
  }

  test("toString should redact a `Set-Cookie` header") {
    val resp = Response().putHeaders(headers.`Set-Cookie`(ResponseCookie("token", "value")))
    assertEquals(resp.toString, "Response(status=200, headers=Headers(Set-Cookie: <REDACTED>))")
  }

  test("not Found should return a plain text UTF-8 not found response") {
    val resp: Response[Pure] = Response.notFound

    assertEquals(resp.contentType, Some(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)))
    assertEquals(resp.status, Status.NotFound)
    assertEquals(resp.body.through(fs2.text.utf8Decode).toList.mkString(""), "Not found")
  }

  test("covary should disallow unrelated effects") {
    illTyped("Response[Option]().covary[IO]")
    true
  }

  test("covary should allow related effects") {
    trait F1[A]
    trait F2[A] extends F1[A]
    Response[F2]().covary[F1]
    true
  }
}
