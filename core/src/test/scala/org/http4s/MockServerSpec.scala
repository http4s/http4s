package org.http4s

import scala.language.implicitConversions
import scala.language.reflectiveCalls


import org.specs2.mutable.Specification
import play.api.libs.iteratee._
import org.specs2.time.NoTimeConversions

import org.http4s.HttpHeaders.RawHeader

class MockServerSpec extends Specification with NoTimeConversions {
  import concurrent.ExecutionContext.Implicits.global

  val server = new MockServer(ExampleRoute())

  "A mock server" should {
    "handle matching routes" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/echo")
      val body = Enumerator("one", "two", "three").map[HttpChunk](s => BodyChunk(s, req.charset))
      new String(server.response(req, body).body) should_==("onetwothree")
    }

    "runs a sum" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/sum")
      val body = Enumerator("1\n", "2\n3", "\n4").map[HttpChunk](s => BodyChunk(s, req.charset))
      new String(server.response(req, body).body) should_==("10")
    }

    "runs too large of a sum" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/sum")
      val body = Enumerator("12345678\n901234567").map[HttpChunk](s => BodyChunk(s, req.charset))
      server.response(req, body).statusLine should_==(Status.RequestEntityTooLarge)
    }

    "not consume the trailer when parsing the body" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/body-and-trailer")
      val body = Enumerator[HttpChunk](
        BodyChunk("1234567890123456"),
        TrailerChunk(HttpHeaders(RawHeader("Hi", "I'm a trailer")))
      )
      new String(server.response(req, body).body) should_==("1234567890123456\nI'm a trailer")
    }

    "fall through to not found" in {
      val req = RequestPrelude(pathInfo = "/bielefield")
      server.response(req).statusLine should_== Status.NotFound
    }

    "handle exceptions" in {
      val req = RequestPrelude(pathInfo = "/fail")
      server.response(req).statusLine should_== Status.InternalServerError
    }

    "Handle futures" in {
      val req = RequestPrelude(pathInfo = "/future")
      val returned = server.response(req)
      returned.statusLine should_== Status.Ok
      new String(returned.body) should_== "Hello from the future!"
    }

    "Do a Go" in {
      val req = RequestPrelude(pathInfo = "/challenge")
      val body = Enumerator[HttpChunk](BodyChunk("Go and do something", req.charset))
      val returned = server.response(req, body)
      returned.statusLine should_== Status.Ok
      new String(returned.body) should_== "Go and do something"
    }

    "Do a NoGo" in {
      val req = RequestPrelude(pathInfo = "/challenge")
      val body = Enumerator[HttpChunk](BodyChunk("NoGo and do something", req.charset))
      val returned = server.response(req, body)
      returned.statusLine should_== Status.BadRequest
      new String(returned.body) should_== "Booo!"
    }

    "Do an Empty Body" in {
      val req = RequestPrelude(pathInfo = "/challenge")
      val returned = server.response(req)
      returned.statusLine should_== Status.BadRequest
      new String(returned.body) should_== "No data!"
    }
  }
}
