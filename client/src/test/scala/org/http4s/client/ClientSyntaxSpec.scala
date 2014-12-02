package org.http4s
package client

import scodec.bits.ByteVector

import scalaz.\/
import scalaz.concurrent.Task

import org.http4s.server.HttpService
import org.http4s.Status.{Ok, NotFound, Created}
import org.http4s.Method._

import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/"    => ResponseBuilder(Ok, "hello")
    case r if r.method == PUT && r.pathInfo == "/put" => ResponseBuilder(Created, r.body)
    case r                                            => sys.error("Path not found: " + r.pathInfo)
  }

  implicit val client = new MockClient(route)

  val req = Request(GET, uri("http://www.foo.bar/"))

  "Client syntax" should {

    "decode based on matched Status" in {
      val resp1 = req.matchStatus {
        case Ok => EntityDecoder.text
      }.run
      resp1 must_== "hello"

      val resp2 = Task(req).matchStatus {
        case Ok => EntityDecoder.text
      }.run
      resp2 must_== "hello"
    }

    "give InvalidResponseException on unmatched Status" in {
      val resp1 = req.matchStatus {
        case NotFound => EntityDecoder.text  // shouldn't match
      }
      resp1.run must throwA[InvalidResponseException]

      val resp2 = Task(req).matchStatus {
        case Ok => EntityDecoder.text
      }.run
      resp2 must_== "hello"
    }

    "be simple to use for any response" in {
      val resp1 = req.withDecoder {
        case Response(Ok, _, _, _, _) => EntityDecoder.text
        case _                        => ???
      }.run
      resp1 must_== "hello"

      val resp2 = Task(req).withDecoder {
        case Response(Ok, _, _, _, _) => EntityDecoder.text
        case _                        => ???
      }.run
      resp2 must_== "hello"
    }
  }

  "Client on syntax" should {

    "support Uris" in {
      req.uri.on(Ok).as[String]
        .run must_== "hello"
    }

    "support Requests" in {
      req.on(Ok).as[String]
        .run must_== "hello"
    }

    "support Task[Request]s" in {
      req.on(Ok).as[String]
        .run must_== "hello"
    }

    "default to Ok if no Status is mentioned" in {
      req.as[String]
        .run must_== "hello"
    }

    "allow multiple Status" in {
      req.on(NotFound, Ok).as[String]
        .run must_== "hello"
    }

    "fail on bad Status" in {
      req.on(NotFound).as[String].run must throwA[InvalidResponseException]
    }

    "implicitly resolve an EntityDecoder" in {
      req.uri.on(Ok).as[String]
        .run must_== "hello"
    }

    "implicitly resolve to get headers and body" in {
      req.uri.on(Ok).as[(Headers, String)].run._2 must_== "hello"
    }

    "be mappable to multiple result types" in {
      req.matchStatus {
        case Ok => EntityDecoder.text.map[ByteVector \/ String](\/.right)
        case _  => EntityDecoder.binary.map[ByteVector \/ String](\/.left)
      }.run must beRightDisjunction("hello")
    }
  }

  "RequestResponseGenerator" should {
    "Generate requests based on Method" in {
      GET("http://www.foo.com/").on(Ok).as[String].run must_== "hello"
//      GET("http://www.foo.com/", "cats").on(Ok).as[String].run must_== "hello"  // Doesn't compile, body not allowed

      // The PUT: /put path just echos the body
      PUT("http://www.foo.com/put").on(Ok, Created).as[String].run must_== ""
      PUT("http://www.foo.com/put", "foo").on(Ok, Created).as[String].run must_== "foo" // body allowed
    }
  }

}
