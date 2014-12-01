package org.http4s
package client

import scalaz.concurrent.Task

import org.http4s.Method.GET
import org.http4s.server.HttpService
import org.http4s.Status.{Ok, NotFound}
import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.pathInfo == "/" => ResponseBuilder(Ok, "hello")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  implicit val client = new MockClient(route)

  val req = Request(GET, uri("http://www.foo.bar/"))

  "Client syntax" should {

    "decode based on status" in {
      val resp1 = req.onStatus {
        case Ok => EntityDecoder.text
      }.run
      resp1.body must_== "hello"

      val resp2 = Task(req).onStatus {
        case Ok => EntityDecoder.text
      }.run
      resp2.body must_== "hello"
    }

    "give InvalidResponseException on unmatched status" in {
      val resp1 = req.onStatus {
        case NotFound => ???  // shouldn't match
      }
      resp1.run must throwA[InvalidResponseException]

      val resp2 = Task(req).onStatus {
        case Ok => EntityDecoder.text
      }.run
      resp2.body must_== "hello"
    }

    "be simple to use for any response" in {
      val resp1 = req.withDecoder {
        case Response(Ok, _, _, _, _) => EntityDecoder.text
        case _                        => ???
      }.run
      resp1.body must_== "hello"

      val resp2 = Task(req).withDecoder {
        case Response(Ok, _, _, _, _) => EntityDecoder.text
        case _                        => ???
      }.run
      resp2.body must_== "hello"
    }
  }

  "Client on syntax" should {

    "support Uris" in {
      req.uri.on(Ok)(EntityDecoder.text).run.body must_== "hello"
    }

    "support Requests" in {
      req.on(Ok)(EntityDecoder.text).run.body must_== "hello"
    }

    "support Task[Request]s" in {
      req.on(Ok)(EntityDecoder.text).run.body must_== "hello"
    }

    "allow multiple status" in {
      req.on(NotFound, Ok)(EntityDecoder.text)
        .run.body must_== "hello"
    }

    "fail on bad status" in {
      req.on(NotFound)(EntityDecoder.text)
        .run must throwA[InvalidResponseException]
    }

    "implicitly resolve an EntityDecoder" in {
      req.uri.on[String](Ok).run.body must_== "hello"
    }
  }

}
