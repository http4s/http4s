package org.http4s.client

import org.http4s.Method._
import org.http4s.client.Client.BadResponse
import org.http4s.server.HttpService
import org.http4s.{EntityDecoder, Status}

import org.specs2.mutable.Specification

class ClientSyntaxSpec extends Specification {

  val route: HttpService = {
    case r if r.pathInfo == "/" => Status.Ok("hello")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  implicit val client = new MockClient(route)

  "Client syntax" should {
    "be simple to use" in {
      val resp = Get("http://www.foo.bar/").on(Status.Ok)(EntityDecoder.text).run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "be simple to use for any status" in {
      val resp = Get("http://www.foo.bar/").decode{ case Status.Ok => EntityDecoder.text}.run
      println(resp.body)

      resp.body.isEmpty must be_==(false)
    }

    "fail on bad status" in {
      Get("http://www.google.com/")
        .decode{ case Status.NotFound => EntityDecoder.text }
        .run must throwA[BadResponse]
    }
  }

}
