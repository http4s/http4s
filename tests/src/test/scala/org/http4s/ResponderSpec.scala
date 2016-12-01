package org.http4s

import Http4s._
import org.specs2.mutable.Specification
import org.http4s.Charset._
import headers._

class ResponderSpec extends Specification {

  val resp = Response(Status.Ok)

  "Responder" should {
    "Change status" in {
      val resp = Response(Status.Ok)

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
      c1.headers should have length (3)
      c1.contentType must beSome(`Content-Type`(MediaType.`text/plain`))

      val c2 = c1.withContentType(Some(`Content-Type`(MediaType.`application/json`, `UTF-8`)))

      c2.contentType must beSome(`Content-Type`(MediaType.`application/json`, `UTF-8`))
      c2.headers.count(_ is `Content-Type`) must_== (1)
      c2.headers.count(_ is `Content-Length`) must_== (1)
      c2.headers.count(_ is Host) must_== (1)
    }

    "Replace headers" in {
      val wHeader = resp.putHeaders(Connection("close".ci))
      wHeader.headers.get(Connection) must beSome(Connection("close".ci))

      val newHeaders = wHeader.removeHeader(Connection)
      newHeaders.headers.get(Connection) should be (None)
    }

    "Set cookie" in {
      resp.addCookie("foo", "bar").headers.get(`Set-Cookie`) must beSome(`Set-Cookie`(org.http4s.Cookie("foo", "bar")))
    }
  }
}
