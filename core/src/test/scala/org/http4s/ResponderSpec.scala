package org.http4s

import org.specs2.mutable.Specification

class ResponderSpec extends Specification {
  val resp = Response(ResponsePrelude(Status.Ok))

  "Responder" should {
    "Change status" in {
      val resp = Response(ResponsePrelude(Status.Ok))

      resp.status must_== Status.Ok

      resp.status(Status.BadGateway).status must_== Status.BadGateway
    }

    "Replace content type" in {
      resp.contentType must_== None
      val c1 = resp.contentType(ContentType.`text/plain`)
      c1.contentType must_== Some(ContentType.`text/plain`)

      val c2 = resp.contentType(ContentType.`application/json`)
      c2.contentType must_== Some(ContentType.`application/json`)

      c2.prelude.headers.count(_.name == HttpHeaders.ContentType.name) must_== 1
    }

    "Replace headers" in {
      val wHeader = resp.addHeader(HttpHeaders.Connection(Nil))
      wHeader.prelude.headers.get(HttpHeaders.Connection) must_== Some(HttpHeaders.Connection(Nil))

      val newHeaders = wHeader.dropHeader(HttpHeaders.Connection)
      newHeaders.prelude.headers.get(HttpHeaders.Connection) must_== None
    }

  }
}
