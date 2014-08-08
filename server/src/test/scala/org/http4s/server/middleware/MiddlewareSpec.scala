package org.http4s
package server
package middleware

import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scalaz.stream.Process._


class MiddlewareSpec extends Specification {
  import org.http4s.server.middleware.URITranslation._

  val pingReq     = Request(requestUri = Uri.fromString("/rootPath/ping").get)

  val awareReq = Request(requestUri = Uri.fromString("/rootPath/checktranslate").get)

  val echoBody = emitSeq(List("one", "two", "three")).map(s => ByteVector(s.getBytes))
  val echoReq = Request(requestMethod = Method.POST,
                          requestUri = Uri.fromString("/rootPath/echo").get,
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
      val req = Request(requestMethod = Method.POST,requestUri = Uri.fromString("/foo/echo").get)
      val badpingReq1 = Request(requestUri = Uri.fromString("/rootPat/ping").get)
      val badpingReq2 = Request(requestUri = Uri.fromString("/rootPathh/ping").get)

      server.apply(req).run.statusLine must_== (Status.NotFound)
      server.apply(badpingReq1).run.statusLine must_== (Status.NotFound)
      server.apply(badpingReq2).run.statusLine must_== (Status.NotFound)
    }


  }
}

