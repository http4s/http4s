/*
package org.http4s.util.middleware

import org.http4s._
import play.api.libs.iteratee.Enumerator
import org.scalatest.{Matchers, WordSpec}

/**
 * @author Bryce Anderson
 * Created on 3/9/13 at 11:10 AM
 */
class MiddlewareSpec extends WordSpec with Matchers {
  import util.middleware.URITranslation._

  import concurrent.ExecutionContext.Implicits.global

  val echoBody = Enumerator("one", "two", "three").map[Chunk](s => BodyChunk(s))
  val echoReq = RequestPrelude(requestMethod = Methods.Post, pathInfo = "/rootPath/echo")

  val pingBody = Enumerator.eof[Chunk]
  val pingReq = RequestPrelude(requestMethod = Methods.Get, pathInfo = "/rootPath/ping")

  "TranslateRoot" should {
    val server = new MockServer(TranslateRoot("/rootPath")(ExampleRoute()))

    "Translate address" in {
      new String(server.response(echoReq, echoBody).body) should equal ("onetwothree")
      new String(server.response(pingReq, pingBody).body) should equal ("pong")
    }

    "Be undefined at non-matching address" in {
      val req = RequestPrelude(requestMethod = Methods.Post, pathInfo = "/foo/echo")
      server.response(req, echoBody) should equal (server.onNotFound)
    }

  }

  "TranslatePath" should {
    val server = new MockServer(TranslatePath{ str =>
      if (str.startsWith("/rootPath"))
        str.substring("/rootPath".length)
      else str
    }(ExampleRoute()))

    "Translate path" in {
      new String(server.response(echoReq, echoBody).body) should equal ("onetwothree")
      new String(server.response(pingReq, pingBody).body) should equal ("pong")
    }

    "Be undefined at non-matching address" in {
      val req = RequestPrelude(requestMethod = Methods.Post, pathInfo = "/foo/echo")
      server.response(req, echoBody) should equal (server.onNotFound)
    }
  }

}
*/
