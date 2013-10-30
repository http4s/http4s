package org.http4s

import org.specs2.mutable.Specification

class ResponderSpec extends Specification {
  val resp = Responder(ResponsePrelude(Status.Ok))

  "Responder" should {
    "Change status" in {
      val resp = Responder(ResponsePrelude(Status.Ok))

      resp.status must_== Status.Ok

      resp.status(Status.BadGateway).status must_== Status.BadGateway
    }

    "Replace content type" in {
      resp.contentType must_== None
      val c1 = resp.addHeader(HttpHeaders.ContentLength(4))
        .contentType(ContentType.`text/plain`)
        .addHeader(HttpHeaders.Host("foo"))

      c1.prelude.headers.count(_.name == HttpHeaders.ContentLength.name) must_== 1
      c1.prelude.headers.length must_== 3
      c1.contentType must_== Some(ContentType.`text/plain`)

      val c2 = c1.contentType(ContentType.`application/json`)

      c2.contentType must_== Some(ContentType.`application/json`)

      c2.prelude.headers.count(_.name == HttpHeaders.ContentType.name) must_== 1
      c2.prelude.headers.count(_.name == HttpHeaders.ContentLength.name) must_== 1
      c2.prelude.headers.count(_.name == HttpHeaders.Host.name) must_== 1
    }

    "Replace headers" in {
      val wHeader = resp.addHeader(HttpHeaders.Connection(Nil))
      wHeader.prelude.headers.get(HttpHeaders.Connection) must_== Some(HttpHeaders.Connection(Nil))

      val newHeaders = wHeader.dropHeader(HttpHeaders.Connection)
      newHeaders.prelude.headers.get(HttpHeaders.Connection) must_== None
    }

  }
}
