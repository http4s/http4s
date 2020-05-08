/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Pure
import java.net.{InetAddress, InetSocketAddress}

import org.http4s.headers.{Authorization, `Content-Type`, `X-Forwarded-For`}
import org.http4s.testing.Http4sLegacyMatchersIO
import _root_.io.chrisdavenport.vault._
import org.http4s.Uri.{Authority, Scheme}

class MessageSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  "Request" >> {
    "ConnectionInfo" should {
      val local = InetSocketAddress.createUnresolved("www.local.com", 8080)
      val remote = InetSocketAddress.createUnresolved("www.remote.com", 45444)

      "get remote connection info when present" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.server must beSome(local)
        r.remote must beSome(remote)
      }

      "not contain remote connection info when not present" in {
        val r = Request()
        r.server must beNone
        r.remote must beNone
      }

      "be utilized to determine the address of server and remote" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.serverAddr must_== local.getHostString
        r.remoteAddr must beSome(remote.getHostString)
      }

      "be utilized to determine the port of server and remote" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.serverPort must_== local.getPort
        r.remotePort must beSome(remote.getPort)
      }

      "be utilized to determine the from value (first X-Forwarded-For if present)" in {
        val forwardedValues =
          NonEmptyList.of(Some(InetAddress.getLocalHost), Some(InetAddress.getLoopbackAddress))
        val r = Request()
          .withHeaders(Headers.of(`X-Forwarded-For`(forwardedValues)))
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.from must_== forwardedValues.head
      }

      "be utilized to determine the from value (remote value if X-Forwarded-For is not present)" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.from must_== Option(remote.getAddress)
      }
    }

    "support cookies" should {
      "contain a Cookie header when an explicit cookie is added" in {
        Request(Method.GET)
          .addCookie(RequestCookie("token", "value"))
          .headers
          .get("Cookie".ci)
          .map(_.value) must beSome("token=value")
      }

      "contain a single Cookie header when multiple explicit cookies are added" in {
        Request(Method.GET)
          .addCookie(RequestCookie("token1", "value1"))
          .addCookie(RequestCookie("token2", "value2"))
          .headers
          .get("Cookie".ci)
          .map(_.value) must beSome("token1=value1; token2=value2")
      }

      "contain a Cookie header when a name/value pair is added" in {
        Request(Method.GET)
          .addCookie("token", "value")
          .headers
          .get("Cookie".ci)
          .map(_.value) must beSome("token=value")
      }

      "contain a single Cookie header when name/value pairs are added" in {
        Request(Method.GET)
          .addCookie("token1", "value1")
          .addCookie("token2", "value2")
          .headers
          .get("Cookie".ci)
          .map(_.value) must beSome("token1=value1; token2=value2")
      }
    }

    "Request.with..." should {
      val path1 = "/path1"
      val path2 = "/somethingelse"
      val attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 3)

      "reset pathInfo if uri is changed" in {
        val originalReq = Request(uri = Uri(path = path1), attributes = attributes)
        val updatedReq = originalReq.withUri(uri = Uri(path = path2))

        updatedReq.scriptName mustEqual ""
        updatedReq.pathInfo mustEqual path2
      }

      "not modify pathInfo if uri is unchanged" in {
        val originalReq = Request(uri = Uri(path = path1), attributes = attributes)
        val updatedReq = originalReq.withMethod(method = Method.DELETE)

        originalReq.pathInfo mustEqual updatedReq.pathInfo
        originalReq.scriptName mustEqual updatedReq.scriptName
      }

      "preserve caret in withPathInfo" in {
        val originalReq = Request(
          uri = Uri(path = "/foo/bar"),
          attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 4))
        val updatedReq = originalReq.withPathInfo("/quux")

        updatedReq.scriptName mustEqual "/foo"
        updatedReq.pathInfo mustEqual "/quux"
      }
    }

    "cookies" should {
      val cookieList = List(
        RequestCookie("test1", "value1"),
        RequestCookie("test2", "value2"),
        RequestCookie("test3", "value3"))

      "be empty if there are no Cookie headers present" in {
        Request(Method.GET).cookies mustEqual List.empty
      }

      "parse discrete HTTP/1 Cookie header(s) into corresponding RequestCookies" in {
        val cookies = Header("Cookie", "test1=value1; test2=value2; test3=value3")
        val request = Request(Method.GET, headers = Headers.of(cookies))
        request.cookies mustEqual cookieList
      }

      "parse discrete HTTP/2 Cookie header(s) into corresponding RequestCookies" in {
        val cookies = Headers.of(
          Header("Cookie", "test1=value1"),
          Header("Cookie", "test2=value2"),
          Header("Cookie", "test3=value3"))
        val request = Request(Method.GET, headers = cookies)
        request.cookies mustEqual cookieList
      }

      "parse HTTP/1 and HTTP/2 Cookie headers on a single request into corresponding RequestCookies" in {
        val cookies = Headers.of(
          Header("Cookie", "test1=value1; test2=value2"), // HTTP/1 style
          Header("Cookie", "test3=value3")) // HTTP/2 style (separate headers for separate cookies)
        val request = Request(Method.GET, headers = cookies)
        request.cookies mustEqual cookieList
      }
    }

    "toString" should {
      "redact an Authorization header" in {
        val request =
          Request[IO](Method.GET).putHeaders(Authorization(BasicCredentials("user", "pass")))
        request.toString must_== ("Request(method=GET, uri=/, headers=Headers(Authorization: <REDACTED>))")
      }

      "redact Cookie Headers" in {
        val request =
          Request[IO](Method.GET).addCookie("token", "value").addCookie("token2", "value2")
        request.toString must_== ("Request(method=GET, uri=/, headers=Headers(Cookie: <REDACTED>))")
      }
    }

    "covary" should {
      "disallow unrelated effects" in {
        illTyped("Request[Option]().covary[IO]")
        true
      }

      "allow related effects" in {
        trait F1[A]
        trait F2[A] extends F1[A]
        Request[F2]().covary[F1]
        true
      }
    }

    "asCurl" should {
      val port = 1234
      val uri = Uri(
        path = "/foo",
        scheme = Some(Scheme.http),
        authority = Some(Authority(port = Some(port)))
      )
      val request = Request[IO](Method.GET, uri)

      "build cURL representation with scheme and authority" in {
        request.asCurl() mustEqual "curl -X GET 'http://localhost:1234/foo'"
      }

      "build cURL representation with headers" in {
        request
          .withHeaders(Header("k1", "v1"), Header("k2", "v2"))
          .asCurl() mustEqual "curl -X GET 'http://localhost:1234/foo' -H 'k1: v1' -H 'k2: v2'"
      }

      "build cURL representation but redact sensitive information on default" in {
        request
          .withHeaders(
            Header("Cookie", "k3=v3; k4=v4"),
            Authorization(BasicCredentials("user", "pass")))
          .asCurl() mustEqual "curl -X GET 'http://localhost:1234/foo' -H 'Cookie: <REDACTED>' -H 'Authorization: <REDACTED>'"
      }

      "build cURL representation but display sensitive headers on demand" in {
        request
          .withHeaders(
            Header("Cookie", "k3=v3; k4=v4"),
            Header("k5", "v5"),
            Authorization(BasicCredentials("user", "pass")))
          .asCurl(_ => false) mustEqual "curl -X GET 'http://localhost:1234/foo' -H 'Cookie: k3=v3; k4=v4' -H 'k5: v5' -H 'Authorization: Basic dXNlcjpwYXNz'"
      }

      "escape quotation marks in header" in {
        request
          .withHeaders(Header("k6", "'v6'"), Header("'k7'", "v7"))
          .asCurl() mustEqual s"""curl -X GET 'http://localhost:1234/foo' -H 'k6: '\\''v6'\\''' -H ''\\''k7'\\'': v7'"""
      }
    }
  }

  "Message" >> {
    "decode" should {
      "produce a UnsupportedMediaType in the event of a decode failure" >> {
        "MediaTypeMismatch" in {
          val req =
            Request[IO](headers = Headers.of(`Content-Type`(MediaType.application.`octet-stream`)))
          val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
          resp.map(_.status) must returnValue(Status.UnsupportedMediaType)
        }
        "MediaTypeMissing" in {
          val req = Request[IO]()
          val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
          resp.map(_.status) must returnValue(Status.UnsupportedMediaType)
        }
      }
    }
  }

  "Response" >> {
    "toString" should {
      "redact a `Set-Cookie` header" in {
        val resp = Response().putHeaders(headers.`Set-Cookie`(ResponseCookie("token", "value")))
        resp.toString must_== ("Response(status=200, headers=Headers(Set-Cookie: <REDACTED>))")
      }
    }

    "notFound" should {
      "return a plain text UTF-8 not found response" in {
        val resp: Response[Pure] = Response.notFound

        resp.contentType must beSome(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`))
        resp.status must_=== Status.NotFound
        resp.body.through(fs2.text.utf8Decode).toList.mkString("") must_=== "Not found"
      }
    }

    "covary" should {
      "disallow unrelated effects" in {
        illTyped("Response[Option]().covary[IO]")
        true
      }

      "allow related effects" in {
        trait F1[A]
        trait F2[A] extends F1[A]
        Response[F2]().covary[F1]
        true
      }
    }
  }
}
