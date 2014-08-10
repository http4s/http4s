package org.http4s

import Http4s._
import org.specs2.mutable.Specification

class ResponderSpec extends Specification {

  val resp = Response(Status.Ok)

  "Responder" should {
    "Change status" in {
      val resp = Response(Status.Ok)

      resp.status must_== (Status.Ok)

      resp.withStatus(Status.BadGateway).status must_== (Status.BadGateway)
    }

    "Replace content type" in {
      import Header._
      resp.contentType should be (None)
      val c1 = resp.addHeaders(Header.`Content-Length`(4))
        .withContentType(Some(`Content-Type`.`text/plain`))
        .addHeaders(Header.Host("foo"))

      c1.headers.count(_ is `Content-Type`) must_== (1)
      c1.headers.count(_ is `Content-Length`) must_== (1)
      c1.headers should have length (3)
      c1.contentType must beSome(`Content-Type`.`text/plain`)

      val c2 = c1.withContentType(Some(`Content-Type`.`application/json`))

      c2.contentType must beSome(`Content-Type`.`application/json`)
      c2.headers.count(_ is `Content-Type`) must_== (1)
      c2.headers.count(_ is `Content-Length`) must_== (1)
      c2.headers.count(_ is Host) must_== (1)
    }

    "Replace headers" in {
      val wHeader = resp.addHeaders(Header.Connection("close".ci))
      wHeader.headers.get(Header.Connection) must beSome(Header.Connection("close".ci))

      val newHeaders = wHeader.removeHeader(Header.Connection)
      newHeaders.headers.get(Header.Connection) should be (None)
    }

    "Set cookie" in {
      resp.addCookie("foo", "bar").headers.get(Header.`Set-Cookie`) must beSome(Header.`Set-Cookie`(Cookie("foo", "bar")))
    }
  }
}
