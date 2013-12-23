
package org.http4s


import org.scalatest.{WordSpec, Matchers}
import org.http4s.util.middleware.PushSupport
import scalaz.stream.Process._
import org.http4s.MockServer.MockResponse
import scalaz.concurrent.Task

class MockServerSpec extends WordSpec with Matchers {

  val server = new MockServer(PushSupport(MockRoute.route()))

  private implicit class respToString(t: Task[MockResponse]) {
    def getString = new String(t.run.body)
    def getStatus = t.run.statusLine
  }

  "A mock server" should {
    "handle matching routes" in {
      val body = emitSeq(List("one", "two", "three")).map[Chunk](s => BodyChunk(s))
      val req = Request(requestMethod = Method.Post, requestUri = RequestUri.fromString("/echo"), body = body)

      server(req).getString should equal ("onetwothree")
    }

    "give a 'not found' for a match error" in {
      val req = Request(requestUri = RequestUri.fromString("/doesntexist/neverwill"))
      server(req).getStatus should equal (Status.NotFound)
    }

    "handle exceptions with an InternalServiceError" in {
      val req = Request(requestUri = RequestUri.fromString("/fail"))
      server(req).getStatus should equal (Status.InternalServerError)
    }

    "Get middleware attributes (For PushSupport)" in {
      val req = Request(requestUri = RequestUri.fromString("/push"))
      val returned = server(req).run
      val pushOptions = returned.attributes.get(PushSupport.pushResponsesKey)

      pushOptions.isDefined shouldNot equal(false)

      val pushResponder = pushOptions.get.run
      pushResponder.length should equal (1)

      pushResponder(0).location should equal("/ping")
    }
  }
}

