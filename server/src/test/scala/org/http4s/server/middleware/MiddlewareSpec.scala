package org.http4s
package server
package middleware

import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scalaz.stream.Process._


class MiddlewareSpec extends Http4sSpec {
  import org.http4s.server.middleware.URITranslation._

  val pingReq     = Request(uri = uri("/rootPath/ping"))

  val awareReq = Request(uri = uri("/rootPath/checktranslate"))

  val echoBody = emitAll(List("one", "two", "three")).map(s => ByteVector(s.getBytes))
  val echoReq = Request(method = Method.POST,
                          uri = uri("/rootPath/echo"),
                          body = echoBody)

  "TranslateRoot" should {
    val server = new MockServer(translateRoot("/rootPath")(MockRoute.route()))


    "Translate address" in {
      new String(server(pingReq).run.body) must_== ("pong")
      new String(server(echoReq).run.body) must_== ("onetwothree")
    }

    "Be aware of translated path" in {
      new String(server(awareReq).run.body) must_==("/rootPath/foo")
    }

    "Be undefined at non-matching address" in {
      val req = Request(method = Method.POST,uri = uri("/foo/echo"))
      val badpingReq1 = Request(uri = uri("/rootPat/ping"))
      val badpingReq2 = Request(uri = uri("/rootPathh/ping"))

      server.apply(req).run.statusLine must_== (Status.NotFound)
      server.apply(badpingReq1).run.statusLine must_== (Status.NotFound)
      server.apply(badpingReq2).run.statusLine must_== (Status.NotFound)
    }


  }
}

