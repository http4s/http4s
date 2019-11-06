package org.http4s
package server
package middleware

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.Uri.{ Authority, RegName, Scheme }


class HttpsRedirectSpec extends Http4sSpec {

  val innerRoutes = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok("pong")
  }

  val reqHeaders = Headers.of(Header("X-Forwarded-Proto", "http"), Header("Host", "example.com"))
  val req        = Request[IO](method = GET, uri = Uri(path = "/"), headers = reqHeaders)

  "HttpsRedirect" should {

    "redirect to https when 'X-Forwarded-Proto' is http" in {
      val app = HttpsRedirect(innerRoutes).orNotFound
      val resp = app(req).unsafeRunSync
      val expectedAuthority = Authority(host = RegName("example.com"))
      val expectedLocation = Location(Uri(path = "/", scheme = Some(Scheme.https), authority = Some(expectedAuthority)))
      val expectedHeaders = Headers(expectedLocation :: `Content-Type`(MediaType.text.xml) :: Nil)
      resp.status must_== Status.MovedPermanently
      resp.headers must_== expectedHeaders
    }

    "not redirect otherwise" in {
      val app = HttpsRedirect(innerRoutes).orNotFound
      val noHeadersReq = Request[IO](method = GET, uri = Uri(path = "/"))
      val resp = app(noHeadersReq).unsafeRunSync
      resp.status must_== Status.Ok
      resp.as[String] must_== "pong"
    }

  }

}
