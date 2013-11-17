/*
package org.http4s

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import play.api.libs.iteratee._
<<<<<<< HEAD
<<<<<<< HEAD
import org.http4s.HttpHeaders.RawHeader
=======

<<<<<<< HEAD
import org.http4s.Headers.RawHeader
>>>>>>> develop
=======
import org.http4s.Header.RawHeader
>>>>>>> develop
=======

import org.http4s.Header
>>>>>>> develop
import org.scalatest.{WordSpec, Matchers}
import scala.concurrent.Future

class MockServerSpec extends WordSpec with Matchers {
  import concurrent.ExecutionContext.Implicits.global

  val server = new MockServer(PushSupport(ExampleRoute()))

  "A mock server" should {
    "handle matching routes" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/echo")
      val body = Enumerator("one", "two", "three").map[Chunk](s => BodyChunk(s, req.charset))
      new String(server.response(req, body).body) should equal ("onetwothree")
    }

    "runs a sum" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/sum")
      val body = Enumerator("1\n", "2\n3", "\n4").map[Chunk](s => BodyChunk(s, req.charset))
      new String(server.response(req, body).body) should equal ("10")
    }

    "runs too large of a sum" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/sum")
      val body = Enumerator("12345678\n901234567").map[Chunk](s => BodyChunk(s, req.charset))
      server.response(req, body).statusLine should equal (Status.RequestEntityTooLarge)
    }

    "not consume the trailer when parsing the body" in {
      val req = RequestPrelude(requestMethod = Method.Post, pathInfo = "/body-and-trailer")
      val body = Enumerator[Chunk](
        BodyChunk("1234567890123456"),
        TrailerChunk(HeaderCollection(Header("Hi", "I'm a trailer")))
      )
      new String(server.response(req, body).body) should equal ("1234567890123456\nI'm a trailer")
    }

    "fall through to not found" in {
      val req = RequestPrelude(pathInfo = "/bielefield")
      server.response(req).statusLine should equal (Status.NotFound)
    }

    "handle exceptions" in {
      val req = RequestPrelude(pathInfo = "/fail")
      server.response(req).statusLine should equal (Status.InternalServerError)
    }

    "Handle futures" in {
      val req = RequestPrelude(pathInfo = "/future")
      val returned = server.response(req)
      returned.statusLine should equal (Status.Ok)
      new String(returned.body) should equal ("Hello from the future!")
    }

    "Do a Go" in {
      val req = RequestPrelude(pathInfo = "/challenge")
      val body = Enumerator[Chunk](BodyChunk("Go and do something", req.charset))
      val returned = server.response(req, body)
      returned.statusLine should equal (Status.Ok)
      new String(returned.body) should equal ("Go and do something")
    }

    "Do a NoGo" in {
      val req = RequestPrelude(pathInfo = "/challenge")
      val body = Enumerator[Chunk](BodyChunk("NoGo and do something", req.charset))
      val returned = server.response(req, body)
      returned.statusLine should equal(Status.BadRequest)
      new String(returned.body) should equal("Booo!")
    }

    "Do an Empty Body" in {
      val req = RequestPrelude(pathInfo = "/challenge")
      val returned = server.response(req)
      returned.statusLine should equal (Status.BadRequest)
      new String(returned.body) should equal ("No data!")
    }

    "Deal with pushed results" in {
      import concurrent.Await
      import concurrent.duration._

      def runBody(body: Enumeratee[Chunk, Chunk]): Future[String] = {
        val responseBodyIt: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume()
        val route = body ><> BodyParser.whileBodyChunk &>> responseBodyIt map { bytes: BodyChunk =>
          new String(bytes.toArray)
        }
        route.run
      }

      val req = RequestPrelude(pathInfo = "/push")
      val returned = server.response(req)
      val pushOptions = returned.attributes.get(PushSupport.pushRespondersKey)

      pushOptions.isDefined shouldNot equal(false)

      val pushResponder = Await.result(pushOptions.get, 5 seconds)
      pushResponder.length should equal (2)

      pushResponder(0).location should equal("/ping")
      pushResponder(1).location should equal("/pushed")

      Await.result(runBody(pushResponder(0).resp.body), 5 seconds) should equal("pong")
      Await.result(runBody(pushResponder(1).resp.body), 5 seconds) should equal("Pushed")
    }
  }


}
*/
