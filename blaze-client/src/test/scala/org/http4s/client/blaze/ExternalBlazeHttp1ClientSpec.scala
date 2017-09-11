package org.http4s.client.blaze

import cats.effect.IO
import org.http4s._
import scala.concurrent.duration._

// TODO: this should have a more comprehensive test suite
class ExternalBlazeHttp1ClientSpec extends Http4sSpec {
  private val timeout = 30.seconds

  private val simpleClient = SimpleHttp1Client[IO]()

  "Blaze Simple Http1 Client" should {
    "Make simple https requests" in {
      val resp = simpleClient.expect[String](uri("https://httpbin.org/get")).unsafeRunTimed(timeout)
      resp.map(_.length > 0) must beSome(true)
    }
  }

  step {
    simpleClient.shutdown.unsafeRunSync()
  }
}
