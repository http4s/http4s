package org.http4s.client.middleware

import org.specs2.mutable.Specification

import cats.effect._
import org.http4s._
import org.http4s.ResponseCookie

class CookieJarSpec extends Specification {

  val epoch: HttpDate = HttpDate.Epoch

  "cookieAppliesToRequest" should {
    "apply if the given domain matches" in {
      val req = Request[IO](Method.GET, uri = Uri.uri("http://google.com"))
      println(req.uri.authority.map(_.renderString))
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
