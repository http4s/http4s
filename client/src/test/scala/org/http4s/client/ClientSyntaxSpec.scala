package org.http4s
package client

import org.http4s.Status.ResponseClass._

import scalaz.concurrent.Task

import org.http4s.server.HttpService
import org.http4s.Status.{Ok, NotFound, Created, BadRequest}
import org.http4s.Method._

import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/"            => ResponseBuilder(Ok, "hello")
    case r if r.method == PUT && r.pathInfo == "/put"         => ResponseBuilder(Created, r.body)
    case r if r.method == GET && r.pathInfo == "/echoheaders" =>
      r.headers.get(Header.Accept).fold(ResponseBuilder.basic(BadRequest)){ m =>
         ResponseBuilder(Ok, m.toString)
      }

    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val client = new MockClient(route)

  val req = Request(GET, uri("http://www.foo.bar/"))

  "Client" should {

    "support Uris" in {
      client(req.uri).as[String]
        .run must_== "hello"
    }

    "support Requests" in {
      client(req).as[String]
        .run must_== "hello"
    }

    "support Task[Request]s" in {
      client(Task(req)).as[String]
        .run must_== "hello"
    }

    "default to Ok if no Status is mentioned" in {
      client(req).as[String]
        .run must_== "hello"
    }

    "use Status for Response matching and extraction" in {
      client(req).map {
        case Ok(resp) => "Ok"
        case _ => "fail"
      }.run must_== "Ok"

      client(req).map {
        case NotFound(resp) => "fail"
        case _ => "nomatch"
      }.run must_== "nomatch"
    }

    "use Status for Response matching and extraction" in {
      client(req).flatMap {
        case Successful(resp) => resp.as[String]
        case _                => Task.now("fail")
      }.run must_== "hello"

      client(req).map {
        case ServerError(resp) => "fail"
        case _ => "nomatch"
      }.run must_== "nomatch"
    }

    "implicitly resolve to get headers and body" in {
      client(req).as[(Headers, String)]
        .run._2 must_== "hello"
    }

    "attemptAs with successful result" in {
      client(req).attemptAs[String]
        .run.run must beRightDisjunction("hello")
    }

    "attemptAs with failed parsing result" in {
      client(req).attemptAs(EntityDecoder.xml())
        .run.run must beLeftDisjunction
    }

    "prepAs must add Accept header" in {
      client.prepAs(GET(uri("http://www.foo.com/echoheaders")))(EntityDecoder.text)
        .run must_== "Accept: text/*"

      client.prepAs[String](GET(uri("http://www.foo.com/echoheaders")))
        .run must_== "Accept: text/*"

      client.prepAs[String](uri("http://www.foo.com/echoheaders"))
        .run must_== "Accept: text/*"

      // Are we combining our mediatypes correctly? This is more of an EntityDecoder spec
      val edec = EntityDecoder[String]({ _: Message => DecodeResult.success("foo!")}, MediaType.`image/jpeg`)
      client.prepAs(GET(uri("http://www.foo.com/echoheaders")))(EntityDecoder.text orElse edec)
        .run must_== "Accept: text/*, image/jpeg"
    }
  }

  "RequestResponseGenerator" should {
    "Generate requests based on Method" in {
      client(GET(uri("http://www.foo.com/"))).as[String]
        .run must_== "hello"
      //      GET("http://www.foo.com/", "cats").on(Ok).as[String].run must_== "hello"  // Doesn't compile, body not allowed

      // The PUT: /put path just echos the body
      client(PUT(uri("http://www.foo.com/put"))).as[String]
        .run must_== ""

      client(PUT(uri("http://www.foo.com/put"), "foo")).as[String] // body allowed
        .run must_== "foo"
    }
  }
}
