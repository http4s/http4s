package org.http4s.util.middleware

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import org.http4s._
import play.api.libs.iteratee.Enumerator
/**
 * @author Bryce Anderson
 * Created on 3/9/13 at 11:10 AM
 */
class MiddlewareSpec extends Specification with NoTimeConversions {
  import util.middleware.URITranslation._

  val echoBody = Enumerator("one", "two", "three").map[HttpChunk](s => BodyChunk(s))
  val echoReq = RequestPrelude(requestMethod = Method.Post, pathInfo = "rootPath/echo")

  val pingBody = Enumerator.eof[HttpChunk]
  val pingReq = RequestPrelude(requestMethod = Method.Get, pathInfo = "rootPath/ping")

  "TranslateRoot" should {
    val server = new MockServer(TranslateRoot("rootPath")(ExampleRoute()))

    "Translate address" in {
      new String(server.response(echoReq, echoBody).body) should_==("onetwothree")
      new String(server.response(pingReq, pingBody).body) should_==("pong")
    }

    "Be undefined at non-matching address" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "foo/echo")
      server.response(req, echoBody) should_== server.onNotFound
    }

  }

  "TranslatePath" should {
    val server = new MockServer(TranslatePath(ExampleRoute()){ str =>
      if (str.startsWith("rootPath/"))
        str.substring("rootPath/".length)
      else str
    })

    "Translate path" in {
      new String(server.response(echoReq, echoBody).body) should_==("onetwothree")
      new String(server.response(pingReq, pingBody).body) should_==("pong")
    }

    "Be undefined at non-matching address" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "foo/echo")
      server.response(req, echoBody) should_== server.onNotFound
    }
  }

}
