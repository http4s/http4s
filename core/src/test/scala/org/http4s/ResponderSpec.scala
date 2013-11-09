package org.http4s

import org.scalatest.{OptionValues, WordSpec, Matchers}

class ResponderSpec extends WordSpec with Matchers with OptionValues {
  val resp = Responder(ResponsePrelude(Status.Ok))

  "Responder" should {
    "Change status" in {
      val resp = Responder(ResponsePrelude(Status.Ok))

      resp.status should equal (Status.Ok)

      resp.status(Status.BadGateway).status should equal (Status.BadGateway)
    }

    "Replace content type" in {
      import Headers._
      resp.contentType should be (None)
      val c1 = resp.addHeader(Headers.`Content-Length`(4))
        .contentType(ContentType.`text/plain`)
        .addHeader(Headers.Host("foo"))

      c1.prelude.headers.count(_ is `Content-Type`) should equal (1)
      c1.prelude.headers.count(_ is `Content-Length`) should equal (1)
      c1.prelude.headers should have length (3)
      c1.contentType.value should equal (ContentType.`text/plain`)

      val c2 = c1.contentType(ContentType.`application/json`)

      c2.contentType.value should equal (ContentType.`application/json`)
      c2.prelude.headers.count(_ is `Content-Type`) should equal (1)
      c2.prelude.headers.count(_ is `Content-Length`) should equal (1)
      c2.prelude.headers.count(_ is Host) should equal (1)
    }

    "Replace headers" in {
      val wHeader = resp.addHeader(Headers.Connection("close"))
      wHeader.prelude.headers.get(Headers.Connection).value should equal (Headers.Connection("close"))

      val newHeaders = wHeader.dropHeader(Headers.Connection)
      newHeaders.prelude.headers.get(Headers.Connection) should be (None)
    }

  }
}
