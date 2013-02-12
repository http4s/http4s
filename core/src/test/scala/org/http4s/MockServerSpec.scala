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
import java.nio.charset.Charset

class MockServerSpec extends Specification with NoTimeConversions {
  import scala.concurrent.ExecutionContext.Implicits.global

  val server = new MockServer(ExampleRoute())

  def response(req: RequestHead, body: Enumerator[Chunk] = Enumerator.eof): MockServer.Response = {
    Await.result(server(req, body), 5 seconds)
  }

  "A mock server" should {
    "handle matching routes" in {
      val req = RequestHead(requestMethod = Method.Post, pathInfo = "/echo")
      val body = Enumerator("one", "two", "three").map(_.getBytes)
      new String(response(req, body).body) should_==("onetwothree")
    }

    "runs a sum" in {
      val req = RequestHead(requestMethod = Method.Post, pathInfo = "/sum")
      val body = Enumerator("1\n", "2\n3", "\n4").map(_.getBytes)
      new String(response(req, body).body) should_==("10")
    }

    "runs too large of a sum" in {
      val req = RequestHead(requestMethod = Method.Post, pathInfo = "/sum")
      val body = Enumerator("12345678\n901234567").map(_.getBytes)
      response(req, body).statusLine should_==(StatusLine.RequestEntityTooLarge)
    }

    "fall through to not found" in {
      val req = RequestHead(pathInfo = "/bielefield")
      response(req).statusLine should_== StatusLine.NotFound
    }

    "handle exceptions" in {
      val req = RequestHead(pathInfo = "/fail")
      response(req).statusLine should_== StatusLine.InternalServerError
    }
  }
}
