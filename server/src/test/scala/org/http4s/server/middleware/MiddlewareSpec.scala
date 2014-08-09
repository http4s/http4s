package org.http4s
package server
package middleware

import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scalaz.stream.Process._


class MiddlewareSpec extends Http4sSpec {
  import org.http4s.server.middleware.URITranslation._

  val pingReq     = Request(requestUri = Uri.fromString("/rootPath/ping").yolo)

  val awareReq = Request(requestUri = Uri.fromString("/rootPath/checktranslate").yolo)

  val echoBody = emitSeq(List("one", "two", "three")).map(s => ByteVector(s.getBytes))
  val echoReq = Request(requestMethod = Method.POST,
                          requestUri = Uri.fromString("/rootPath/echo").yolo,
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
      val req = Request(requestMethod = Method.POST,requestUri = Uri.fromString("/foo/echo").yolo)
      val badpingReq1 = Request(requestUri = Uri.fromString("/rootPat/ping").yolo)
      val badpingReq2 = Request(requestUri = Uri.fromString("/rootPathh/ping").yolo)

      server.apply(req).run.statusLine must_== (Status.NotFound)
      server.apply(badpingReq1).run.statusLine must_== (Status.NotFound)
      server.apply(badpingReq2).run.statusLine must_== (Status.NotFound)
    }


  }
}

