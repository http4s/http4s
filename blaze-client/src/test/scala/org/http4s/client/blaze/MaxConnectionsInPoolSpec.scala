package org.http4s.client.blaze

import cats.effect.IO
import org.http4s._

import scala.concurrent.duration._
import scala.util.Random

class MaxConnectionsInPoolSpec extends Http4sSpec {
  private val timeout = 30.seconds

  private val failClient = PooledHttp1Client[IO](maxConnectionsPerRequestKey = _ => 0)
  private val successClient = PooledHttp1Client[IO](maxConnectionsPerRequestKey = _ => 1)
  private val client = PooledHttp1Client[IO](maxConnectionsPerRequestKey = _ => 3)

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

  "Blaze Pooled Http1 Client" should {
    "Behave and not deadlock" in {
      val hosts = Vector(
        uri("https://httpbin.org/get"),
        uri("https://www.google.co.in/"),
        uri("https://www.amazon.com/"),
        uri("https://news.ycombinator.com/"),
        uri("https://duckduckgo.com/"),
        uri("https://www.bing.com/"),
        uri("https://www.reddit.com/")
      )

      (0 until 42)
        .map { _ =>
          val h = hosts(Random.nextInt(hosts.length))
          val resp =
            client.expect[String](h).unsafeRunTimed(timeout)
          resp.map(_.length > 0)
        }
        .forall(_.contains(true)) must beTrue
    }
  }

  step {
    failClient.shutdown.unsafeRunSync()
    successClient.shutdown.unsafeRunSync()
    client.shutdown.unsafeRunSync()
  }
}
