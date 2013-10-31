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
      resp.contentType should be (None)
      val c1 = resp.addHeader(HttpHeaders.ContentLength(4))
        .contentType(ContentType.`text/plain`)
        .addHeader(HttpHeaders.Host("foo"))

      c1.prelude.headers.count(_.name == HttpHeaders.ContentLength.name) should equal (1)
      c1.prelude.headers should have length (3)
      c1.contentType.value should equal (ContentType.`text/plain`)

      val c2 = c1.contentType(ContentType.`application/json`)

      c2.contentType.value should equal (ContentType.`application/json`)

      c2.prelude.headers.count(_.name == HttpHeaders.ContentType.name) should equal (1)
      c2.prelude.headers.count(_.name == HttpHeaders.ContentLength.name) should equal (1)
      c2.prelude.headers.count(_.name == HttpHeaders.Host.name) should equal (1)
    }

    "Replace headers" in {
      val wHeader = resp.addHeader(HttpHeaders.Connection(Nil))
      wHeader.prelude.headers.get(HttpHeaders.Connection).value should equal (HttpHeaders.Connection(Nil))

      val newHeaders = wHeader.dropHeader(HttpHeaders.Connection)
      newHeaders.prelude.headers.get(HttpHeaders.Connection) should be (None)
    }

  }
}
