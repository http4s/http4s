package org.http4s
package client

import scalaz.concurrent.Task

import org.http4s.Method.GET
import org.http4s.client.Client.BadResponse
import org.http4s.server.{HttpService, Service}
import org.http4s.Status.{Ok, NotFound}
import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.pathInfo == "/" => ResponseBuilder(Ok, "hello")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  implicit val client = new MockClient(route)

  "Client syntax" should {
    val req = Request(GET, uri("http://www.foo.bar/"))

    "be simple to use" in {
      val resp = Task.now(Request(GET, uri("http://www.foo.bar/"))).on(Ok)(EntityDecoder.text).run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "be simple to use for any status" in {
      val resp1 = req.decodeStatus {
        case Ok => EntityDecoder.text
      }.run
      resp1.body must_== "hello"

      val resp2 = Task(req).decodeStatus {
        case Ok => EntityDecoder.text
      }.run
      resp2.body must_== "hello"
    }

    "be simple to use for any response" in {
      val resp1 = req.decode {
        case Response(Ok,_,_,_,_) => EntityDecoder.text
      }.run
      resp1.body must_== "hello"

      val resp2 = Task(req).decode {
        case Response(Ok,_,_,_,_) => EntityDecoder.text
      }.run
      resp2.body must_== "hello"
    }

    "fail on bad status" in {
      Task.now(Request(GET, uri("http://www.google.com/")))
        .on(NotFound)(EntityDecoder.text)
        .run must throwA[BadResponse]
    }
  }

}
