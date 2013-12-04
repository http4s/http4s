package org.http4s

import org.scalatest.{OptionValues, WordSpec, Matchers}

class ResponderSpec extends WordSpec with Matchers with OptionValues {
  val resp = Response(ResponsePrelude(Status.Ok))

  "Responder" should {
    "Change status" in {
      val resp = Response(ResponsePrelude(Status.Ok))

      resp.status should equal (Status.Ok)

      resp.status(Status.BadGateway).status should equal (Status.BadGateway)
    }

    "Replace content type" in {
      import Header._
      resp.contentType should be (None)
      val c1 = resp.addHeader(Header.`Content-Length`(4))
        .withContentType(Some(ContentType.`text/plain`))
        .addHeader(Header.Host("foo"))

      c1.prelude.headers.count(_ is `Content-Type`) should equal (1)
      c1.prelude.headers.count(_ is `Content-Length`) should equal (1)
      c1.prelude.headers should have length (3)
      c1.contentType.value should equal (ContentType.`text/plain`)

      val c2 = c1.withContentType(Some(ContentType.`application/json`))

      c2.contentType.value should equal (ContentType.`application/json`)
      c2.prelude.headers.count(_ is `Content-Type`) should equal (1)
      c2.prelude.headers.count(_ is `Content-Length`) should equal (1)
      c2.prelude.headers.count(_ is Host) should equal (1)
    }

    "Replace headers" in {
      val wHeader = resp.addHeader(Header.Connection("close"))
      wHeader.prelude.headers.get(Header.Connection).value should equal (Header.Connection("close"))

      val newHeaders = wHeader.dropHeader(Header.Connection)
      newHeaders.prelude.headers.get(Header.Connection) should be (None)
    }

  }
}
