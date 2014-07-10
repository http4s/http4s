package org.http4s.server.middleware

import org.http4s._
import org.scalatest.{Matchers, WordSpec}

import scalaz.stream.Process
import Process._
import scodec.bits.ByteVector

/**
* @author Bryce Anderson
* Created on 3/9/13 at 11:10 AM
*/
class MiddlewareSpec extends WordSpec with Matchers {
  import org.http4s.server.middleware.URITranslation._

  val pingReq     = Request(requestUri = Uri.fromString("/rootPath/ping").get)

  val awareReq = Request(requestUri = Uri.fromString("/rootPath/checktranslate").get)

  val echoBody = emitSeq(List("one", "two", "three")).map(s => ByteVector(s.getBytes))
  val echoReq = Request(requestMethod = Method.Post,
                          requestUri = Uri.fromString("/rootPath/echo").get,
                          body = echoBody)

  "TranslateRoot" should {
    val server = new MockServer(translateRoot("/rootPath")(MockRoute.route()))


    "Translate address" in {
      new String(server(pingReq).run.body) should equal ("pong")
      new String(server(echoReq).run.body) should equal ("onetwothree")
    }

    "Be aware of translated path" in {
      new String(server(awareReq).run.body) should equal("/rootPath/foo")
    }

    "Be undefined at non-matching address" in {
      val req = Request(requestMethod = Method.Post,requestUri = Uri.fromString("/foo/echo").get)
      val badpingReq1 = Request(requestUri = Uri.fromString("/rootPat/ping").get)
      val badpingReq2 = Request(requestUri = Uri.fromString("/rootPathh/ping").get)

      server.apply(req).run.statusLine should equal (Status.NotFound)
      server.apply(badpingReq1).run.statusLine should equal (Status.NotFound)
      server.apply(badpingReq2).run.statusLine should equal (Status.NotFound)
    }


  }
}

