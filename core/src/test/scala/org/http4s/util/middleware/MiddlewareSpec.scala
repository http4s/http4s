package org.http4s.util.middleware

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import org.http4s._
import play.api.libs.iteratee.Enumerator
import concurrent.duration._

class PathSpec extends Specification

/**
 * @author Bryce Anderson
 * Created on 3/9/13 at 11:10 AM
 */
class MiddlewareSpec extends Specification with NoTimeConversions {

  "TranslateMount" should {
    val server = new MockServer(util.middleware.TranslateMount("rootPath")(ExampleRoute()))

    "Translate address" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "rootPath/echo")
      val body = Enumerator("one", "two", "three").map[HttpChunk](s => BodyChunk(s, req.charset))
      new String(server.response(req, body).body) should_==("onetwothree")
    }

  }

}
