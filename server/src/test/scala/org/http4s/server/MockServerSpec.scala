package org.http4s
package server

import org.http4s.server.MockServer._
import org.http4s.server.middleware.PushSupport
import org.scalatest.{Matchers, WordSpec}
import scodec.bits.ByteVector

import scalaz.concurrent.Task
import scalaz.stream.Process._

class MockServerSpec extends WordSpec with Matchers {

  val server = new MockServer(PushSupport(MockRoute.route()))

  private implicit class respToString(t: Task[MockResponse]) {
    def getString = new String(t.run.body)
    def getStatus = t.run.statusLine
  }

  "A mock server" should {
    "handle matching routes" in {
      val body = emitSeq(List("one", "two", "three")).map(s => ByteVector(s.getBytes))
      val req = Request(requestMethod = Method.Post, requestUri = Uri.fromString("/echo").get, body = body)

      server(req).getString should equal ("onetwothree")
    }

    "give a 'not found' for a match error" in {
      val req = Request(requestUri = Uri.fromString("/doesntexist/neverwill").get)
      server(req).getStatus should equal (Status.NotFound)
    }

    "handle exceptions with an InternalServiceError" in {
      val req = Request(requestUri = Uri.fromString("/fail").get)
      server(req).getStatus should equal (Status.InternalServerError)
    }

    "Get middleware attributes (For PushSupport)" in {
      val req = Request(requestUri = Uri.fromString("/push").get)
      val returned = server(req).run
      val pushOptions = returned.attributes.get(PushSupport.pushResponsesKey)

      pushOptions.isDefined shouldNot equal(false)

      val pushResponder = pushOptions.get.run
      pushResponder.length should equal (1)

      pushResponder(0).location should equal("/ping")
    }
  }
}

