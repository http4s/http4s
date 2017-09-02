package org.http4s.client.blaze

import cats.effect.IO
import org.http4s._

import scala.concurrent.duration._

class MaxConnectionsInPoolSpec extends Http4sSpec {
  private val timeout = 30.seconds

  private val failClient = PooledHttp1Client[IO](maxConnectionsPerRequestKey = _ => 0)
  private val successClient = PooledHttp1Client[IO](maxConnectionsPerRequestKey = _ => 1)

  "Blaze Pooled Http1 Client with zero max connections" should {
    "Not make simple https requests" in {
      val resp = failClient.expect[String](uri("https://httpbin.org/get")).unsafeRunTimed(timeout)
      resp.map(_.length > 0) must beNone
    }
  }

  "Blaze Pooled Http1 Client" should {
    "Make simple https requests" in {
      val resp =
        successClient.expect[String](uri("https://httpbin.org/get")).unsafeRunTimed(timeout)
      resp.map(_.length > 0) must beSome(true)
    }
  }

  step {
    failClient.shutdown.unsafeRunSync()
    successClient.shutdown.unsafeRunSync()
  }
}
