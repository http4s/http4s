
package org.http4s.util.middleware

import org.http4s._
import org.scalatest.{Matchers, WordSpec}

import scalaz.stream.Process
import Process._
import org.http4s.Status.Ok
import scalaz.concurrent.Task

/**
* @author Bryce Anderson
* Created on 3/9/13 at 11:10 AM
*/
class MiddlewareSpec extends WordSpec with Matchers {
  import util.middleware.URITranslation._

  val pingReq = Request(requestUri = RequestUri.fromString("/rootPath/ping"))

  val awareReq = Request(requestUri = RequestUri.fromString("/rootPath/checktranslate"))

  val echoBody = emitSeq(List("one", "two", "three")).map[Chunk](s => BodyChunk(s))
  val echoReq = Request(requestMethod = Method.Post,
                          requestUri = RequestUri.fromString("/rootPath/echo"),
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
      val req = Request(requestMethod = Method.Post,requestUri = RequestUri.fromString("/foo/echo"))
      server.apply(req).run.statusLine should equal (Status.NotFound)
    }


  }
}

