package org.http4s.client.middleware

import org.specs2.mutable.Specification

import cats.implicits._
import cats.effect._
import cats.effect.testing.specs2.CatsIO
import org.http4s._
import org.http4s.implicits._
import org.http4s.client._
import org.http4s.ResponseCookie
import org.http4s.dsl.io._
import org.http4s.headers.Cookie

class CookieJarSpec extends Specification with CatsIO {

  val epoch: HttpDate = HttpDate.Epoch

  "CookieJar middleware" should {
    "extract a cookie and apply it correctly" in {
      val routes = HttpRoutes
        .of[IO] {
          case GET -> Root / "get-cookie" =>
            val resp = Response[IO](Status.Ok).addCookie(
              ResponseCookie(
                name = "foo",
                content = "bar",
                domain = Some("google.com"),
                expires = HttpDate.MaxValue.some
              ))
            resp.pure[IO]
          case req @ GET -> Root / "test-cookie" =>
            req.headers
              .get(Cookie)
              .fold(
                Response[IO](Status.InternalServerError)
              )(_ => Response[IO](Status.Ok))
              .pure[IO]
        }
        .orNotFound

      val client = Client.fromHttpApp(routes)

      for {
        jar <- CookieJar.jarImpl[IO]
        testClient = CookieJar(jar)(client)
        _ <- testClient.successful(Request[IO](Method.GET, uri"http://google.com/get-cookie"))
        second <- testClient.successful(Request[IO](Method.GET, uri"http://google.com/test-cookie"))
      } yield second must_=== true
    }
  }

  "cookieAppliesToRequest" should {
    "apply if the given domain matches" in {
      val req = Request[IO](Method.GET, uri = uri"http://google.com")
      val cookie = ResponseCookie(
        "foo",
        "bar",
        domain = Some("google.com")
      )
      CookieJar.cookieAppliesToRequest(req, cookie) must_=== true
    }

    "not apply if not given a domain" in {
      val req = Request[IO](Method.GET, uri = Uri.uri("http://google.com"))
      val cookie = ResponseCookie(
        "foo",
        "bar",
        domain = None
      )
      CookieJar.cookieAppliesToRequest(req, cookie) must_=== false
    }

    "apply if a subdomain" in {
      val req = Request[IO](Method.GET, uri = Uri.uri("http://api.google.com"))
      val cookie = ResponseCookie(
        "foo",
        "bar",
        domain = Some("google.com")
      )
      CookieJar.cookieAppliesToRequest(req, cookie) must_=== true
    }

    "not apply if the wrong subdomain" in {
      val req = Request[IO](Method.GET, uri = Uri.uri("http://api.google.com"))
      val cookie = ResponseCookie(
        "foo",
        "bar",
        domain = Some("bad.google.com")
      )
      CookieJar.cookieAppliesToRequest(req, cookie) must_=== false
    }

    "not apply if the superdomain" in {
      val req = Request[IO](Method.GET, uri = Uri.uri("http://google.com"))
      val cookie = ResponseCookie(
        "foo",
        "bar",
        domain = Some("bad.google.com")
      )
      CookieJar.cookieAppliesToRequest(req, cookie) must_=== false
    }

  }

}
