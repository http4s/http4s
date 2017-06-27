package org.http4s

import java.time.Instant

import cats.effect.IO
import org.http4s.Charset._
import org.http4s.Http4s._
import org.http4s.headers._
import org.specs2.mutable.Specification

class ResponderSpec extends Specification {

  val resp = Response[IO](Status.Ok)

  "Responder" should {
    "Change status" in {
      val resp = Response[IO](Status.Ok)

      resp.status must_== (Status.Ok)

      resp.withStatus(Status.BadGateway).status must_== (Status.BadGateway)
    }

    "Replace content type" in {
      resp.contentType should be (None)
      val c1 = resp.putHeaders(`Content-Length`(4))
        .withContentType(Some(`Content-Type`(MediaType.`text/plain`)))
        .putHeaders(Host("foo"))

      c1.headers.count(_ is `Content-Type`) must_== (1)
      c1.headers.count(_ is `Content-Length`) must_== (1)
      c1.headers must have length (3)
      c1.contentType must beSome(`Content-Type`(MediaType.`text/plain`))

      val c2 = c1.withContentType(Some(`Content-Type`(MediaType.`application/json`, `UTF-8`)))

      c2.contentType must beSome(`Content-Type`(MediaType.`application/json`, `UTF-8`))
      c2.headers.count(_ is `Content-Type`) must_== (1)
      c2.headers.count(_ is `Content-Length`) must_== (1)
      c2.headers.count(_ is Host) must_== (1)
    }

    "Remove headers" in {
      val wHeader = resp.putHeaders(Connection("close".ci))
      wHeader.headers.get(Connection) must beSome(Connection("close".ci))

      val newHeaders = wHeader.removeHeader(Connection)
      newHeaders.headers.get(Connection) must beNone
    }

    "Replace all headers" in {
      val wHeader = resp.putHeaders(Connection("close".ci), `Content-Length`(10), Host("foo"))
      wHeader.headers.toList must have length 3

      val newHeaders = wHeader.replaceAllHeaders(Date(Instant.now))
      newHeaders.headers.toList must have length 1
      newHeaders.headers.get(Connection) must beNone
    }

    "Replace all headers II" in {
      val wHeader = resp.putHeaders(Connection("close".ci), `Content-Length`(10), Host("foo"))
      wHeader.headers.toList must have length 3

      val newHeaders = wHeader.replaceAllHeaders(Headers(Date(Instant.now)))
      newHeaders.headers.toList must have length 1
      newHeaders.headers.get(Connection) must beNone
    }

    "Filter headers" in {
      val wHeader = resp.putHeaders(Connection("close".ci), `Content-Length`(10), Host("foo"))
      wHeader.headers.toList must have length 3

      val newHeaders = wHeader.filterHeaders(_.name != "Connection".ci)
      newHeaders.headers.toList must have length 2
      newHeaders.headers.get(Connection) must beNone
    }

    "Set cookie" in {
      resp.addCookie("foo", "bar").headers.get(`Set-Cookie`) must beSome(`Set-Cookie`(org.http4s.Cookie("foo", "bar")))
      resp.addCookie(Cookie("foo", "bar")).headers.get(`Set-Cookie`) must beSome(`Set-Cookie`(org.http4s.Cookie("foo", "bar")))
    }

    "Remove cookie" in {
      val cookie = Cookie("foo", "bar")
      resp.removeCookie(cookie).headers.get(`Set-Cookie`) must
        beSome(`Set-Cookie`(org.http4s.Cookie("foo", "", expires = Option(Instant.ofEpochSecond(0)), maxAge = Some(0L))))
    }
  }
}
