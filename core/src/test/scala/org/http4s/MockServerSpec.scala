package org.http4s

import scala.language.implicitConversions
import scala.language.reflectiveCalls

import scala.concurrent.Await
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import play.api.libs.iteratee._
import org.specs2.time.NoTimeConversions

import Writable._
import Bodies._

class MockServerSpec extends Specification with NoTimeConversions {
  import scala.concurrent.ExecutionContext.Implicits.global

  val server = new MockServer({
    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Done(Responder(body = req.body))
    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      Enumeratee.map[Chunk] { case chunk => new String(chunk).toInt }
        .transform(Iteratee.fold(0) { _ + _ })
        .map { sum => Responder(body = sum) }
    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  })

  def response(req: Request): MockServer.Response = {
    Await.result(server(req), 5 seconds)
  }

  "A mock server" should {
    "handle matching routes" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/echo",
        body = Seq("one", "two", "three"))
      new String(response(req).body) should_==("onetwothree")
    }

    "runs a sum" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/sum",
        body = Seq(1, 2, 3))
      new String(response(req).body) should_==("6")
    }

    "fall through to not found" in {
      val req = Request(pathInfo = "/bielefield")
      response(req).statusLine should_== StatusLine.NotFound
    }

    "handle exceptions" in {
      val req = Request(pathInfo = "/fail")
      response(req).statusLine should_== StatusLine.InternalServerError
    }
  }
}
