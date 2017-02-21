package org.http4s.client.blaze

import scala.concurrent.duration._
import fs2._

import org.http4s._

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec {
  private val timeout = 30.seconds

  private val simpleClient = SimpleHttp1Client()

  "Blaze Simple Http1 Client" should {
    "Make simple https requests" in {
      val resp = simpleClient.expect[String](uri("https://httpbin.org/get")).unsafeRunFor(timeout)
      resp.length mustNotEqual 0
    }
  }

  step {
    simpleClient.shutdown.unsafeRun()
  }
}
