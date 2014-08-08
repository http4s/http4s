package org.http4s
package client

import org.http4s.Method.GET
import org.http4s.client.Client.BadResponse
import org.http4s.server.HttpService
import org.http4s.Status.{Ok, NotFound}


import org.specs2.mutable.Specification

class ClientSyntaxSpec extends Specification {

  val route: HttpService = {
    case r if r.pathInfo == "/" => Ok("hello")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  implicit val client = new MockClient(route)

  "Client syntax" should {
    "be simple to use" in {
      val resp = GET("http://www.foo.bar/").on(Ok)(EntityDecoder.text).run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "be simple to use for any status" in {
      val resp = GET("http://www.foo.bar/").decode{ case Response(Ok,_,_,_,_) => EntityDecoder.text}.run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "fail on bad status" in {
      GET("http://www.google.com/")
        .on(NotFound)(EntityDecoder.text)
        .run must throwA[BadResponse]
    }
  }

}
