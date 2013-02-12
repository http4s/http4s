package org.http4s

import scala.language.implicitConversions
import concurrent.Future
import scala.language.reflectiveCalls

import concurrent.{Promise, Future, Await}
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import play.api.libs.iteratee._
import org.specs2.time.NoTimeConversions
import scala.io.Codec

import Writable._
import Bodies._
import java.nio.charset.Charset

class MockServerSpec extends Specification with NoTimeConversions {
  import scala.concurrent.ExecutionContext.Implicits.global

  val server = new MockServer(ExampleRoute())

  def response(req: Request[Raw]): MockServer.Response = {
    Await.result(server(req), 5 seconds)
  }

  "A mock server" should {
    "handle matching routes" in {
      val req = Request[Raw](requestMethod = Method.Post, pathInfo = "/echo",
        body = Enumerator("one", "two", "three").map(_.getBytes))
      new String(response(req).body) should_==("onetwothree")
    }

    "runs a sum" in {
      val req = Request[Raw](requestMethod = Method.Post, pathInfo = "/sum",
        body = Enumerator("1\n", "2\n3", "\n4").map(_.getBytes))
      new String(response(req).body) should_==("10")
    }

    "runs too large of a sum" in {
      val req = Request[Raw](requestMethod = Method.Post, pathInfo = "/sum",
        body = Enumerator("12345678\n901234567").map(_.getBytes))
      response(req).statusLine should_==(StatusLine.RequestEntityTooLarge)
    }

    "fall through to not found" in {
      val req = Request[Raw](pathInfo = "/bielefield", body = EmptyRequestBody)
      response(req).statusLine should_== StatusLine.NotFound
    }

    "handle exceptions" in {
      val req = Request[Raw](pathInfo = "/fail", body = EmptyRequestBody)
      response(req).statusLine should_== StatusLine.InternalServerError
    }

    "Do a Go" in {
      val req = Request[Raw](pathInfo = "/challenge", body = Enumerator("Go and do something".getBytes))
      val returned = response(req)
      returned.statusLine should_== StatusLine.Ok
      new String(returned.body) should_== "Go and do something"
    }

    "Do a NoGo" in {
      val req = Request[Raw](pathInfo = "/challenge", body = Enumerator("Go and do something".getBytes))
      val returned = response(req)
      returned.statusLine should_== StatusLine.BadRequest
      new String(returned.body) should_== "Booo!"
    }

    "Do an Empty Body" in {
      val req = Request[Raw](pathInfo = "/challenge", body = Enumerator.eof)
      val returned = response(req)
      returned.statusLine should_== StatusLine.BadRequest
      new String(returned.body) should_== "No data!"
    }
  }
}
