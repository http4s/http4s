package org.http4s

import org.scalatest.{OptionValues, WordSpec, Matchers}

class ResponderSpec extends WordSpec with Matchers with OptionValues {
  val resp = Response(Status.Ok)

  "Responder" should {
    "Change status" in {
      val resp = Response(Status.Ok)

      resp.status should equal (Status.Ok)

      resp.withStatus(Status.BadGateway).status should equal (Status.BadGateway)
    }

    "Replace content type" in {
      import Header._
      resp.contentType should be (None)
      val c1 = resp.addHeader(Header.`Content-Length`(4))
        .withContentType(Some(`Content-Type`.`text/plain`))
        .addHeader(Header.Host("foo"))

      c1.headers.count(_ is `Content-Type`) should equal (1)
      c1.headers.count(_ is `Content-Length`) should equal (1)
      c1.headers should have length (3)
      c1.contentType.value should equal (`Content-Type`.`text/plain`)

      val c2 = c1.withContentType(Some(`Content-Type`.`application/json`))

      c2.contentType.value should equal (`Content-Type`.`application/json`)
      c2.headers.count(_ is `Content-Type`) should equal (1)
      c2.headers.count(_ is `Content-Length`) should equal (1)
      c2.headers.count(_ is Host) should equal (1)
    }

    "Replace headers" in {
      val wHeader = resp.addHeader(Header.Connection("close"))
      wHeader.headers.get(Header.Connection).value should equal (Header.Connection("close"))

      val newHeaders = wHeader.removeHeader(Header.Connection)
      newHeaders.headers.get(Header.Connection) should be (None)
    }

  }
}
