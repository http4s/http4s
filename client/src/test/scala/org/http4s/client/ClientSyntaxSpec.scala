package org.http4s
package client

import scalaz.concurrent.Task

import org.http4s.Method.GET
import org.http4s.client.Client.BadResponse
import org.http4s.server.HttpService
import org.http4s.Status.{Ok, NotFound}
import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route: HttpService = {
    case r if r.pathInfo == "/" => ResponseBuilder(Ok, "hello")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  implicit val client = new MockClient(route)

  "Client syntax" should {
    "be simple to use" in {
      val resp = Task.now(Request(GET, uri("http://www.foo.bar/"))).on(Ok)(EntityDecoder.text).run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "be simple to use for any status" in {
      val resp = Task.now(Request(GET, uri("http://www.foo.bar/"))).decode{ case Response(Ok,_,_,_,_) => EntityDecoder.text}.run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "fail on bad status" in {
      Task.now(Request(GET, uri("http://www.google.com/")))
        .on(NotFound)(EntityDecoder.text)
        .run must throwA[BadResponse]
    }
  }

}
