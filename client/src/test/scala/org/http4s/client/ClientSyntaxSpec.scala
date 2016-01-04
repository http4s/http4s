package org.http4s
package client

import org.http4s.Status.ResponseClass._
import org.http4s.headers.Accept
import org.parboiled2.ParseError

import scalaz.concurrent.Task

import org.http4s.server.HttpService
import org.http4s.Status.{Ok, NotFound, Created, BadRequest}
import org.http4s.Method._

import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/"            => Response(Ok).withBody("hello")
    case r if r.method == PUT && r.pathInfo == "/put"         => Response(Created).withBody(r.body)
    case r if r.method == GET && r.pathInfo == "/echoheaders" =>
      r.headers.get(Accept).fold(Task.now(Response(BadRequest))){ m =>
         Response(Ok).withBody(m.toString)
      }

    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val client = new MockClient(route)

  val req = Request(GET, uri("http://www.foo.bar/"))

  "Client" should {

    "support Uris" in {
      client.getAs[String](req.uri)
        .run must_== "hello"
    }

    "support Requests" in {
      client.fetchAs[String](req)
        .run must_== "hello"
    }

    "support Task[Request]s" in {
      client.fetchAs[String](Task(req))
        .run must_== "hello"
    }

    "default to Ok if no Status is mentioned" in {
      client.fetchAs[String](req)
        .run must_== "hello"
    }

    "use Status for Response matching and extraction" in {
      client.fetch(req) {
        case Ok(resp) => Task.now("Ok")
        case _ => Task.now("fail")
      }.run must_== "Ok"

      client.fetch(req) {
        case NotFound(resp) => Task.now("fail")
        case _ => Task.now("nomatch")
      }.run must_== "nomatch"
    }

    "use Status for Response matching and extraction" in {
      client.fetch(req) {
        case Successful(resp) => resp.as[String]
        case _                => Task.now("fail")
      }.run must_== "hello"

      client.fetch(req) {
        case ServerError(resp) => Task.now("fail")
        case _ => Task.now("nomatch")
      }.run must_== "nomatch"
    }

    "implicitly resolve to get headers and body" in {
      client.fetchAs[(Headers, String)](req)
        .run._2 must_== "hello"
    }

    "attemptAs with successful result" in {
      client.fetchAs[String](req).attempt
        .run must be_\/-("hello")
    }

    "attemptAs with failed parsing result" in {
      val grouchyEncoder = EntityDecoder.decodeBy[Any](MediaRange.`*/*`) { _ =>
        DecodeResult.failure(ParseFailure("MEH!", "MEH!"))
      }
      client.fetchAs[Any](req)(grouchyEncoder).attempt.run must be_-\/
    }

    "prepAs must add Accept header" in {
      client.fetchAs(GET(uri("http://www.foo.com/echoheaders")))(EntityDecoder.text)
        .run must_== "Accept: text/*"

      client.fetchAs[String](GET(uri("http://www.foo.com/echoheaders")))
        .run must_== "Accept: text/*"

      client.getAs[String](uri("http://www.foo.com/echoheaders"))
        .run must_== "Accept: text/*"

      // Are we combining our mediatypes correctly? This is more of an EntityDecoder spec
      val edec = EntityDecoder.decodeBy(MediaType.`image/jpeg`)(_ => DecodeResult.success("foo!"))
      client.fetchAs(GET(uri("http://www.foo.com/echoheaders")))(EntityDecoder.text orElse edec)
        .run must_== "Accept: text/*, image/jpeg"
    }
  }

  "RequestResponseGenerator" should {
    "Generate requests based on Method" in {
      client.fetchAs[String](GET(uri("http://www.foo.com/")))
        .run must_== "hello"
      //      GET("http://www.foo.com/", "cats").on(Ok).as[String].run must_== "hello"  // Doesn't compile, body not allowed

      // The PUT: /put path just echos the body
      client.fetchAs[String](PUT(uri("http://www.foo.com/put")))
        .run must_== ""

      client.fetchAs[String](PUT(uri("http://www.foo.com/put"), "foo")) // body allowed
        .run must_== "foo"
    }
  }
}
