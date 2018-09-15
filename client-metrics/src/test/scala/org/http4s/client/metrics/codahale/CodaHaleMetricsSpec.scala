package org.http4s.client.metrics.codahale

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.http4s.{Http4sSpec, HttpApp, Response, Status}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.metrics.core.Metrics
import org.http4s.client.metrics.codahale.CodaHaleOps._
import org.http4s.dsl.io._
import org.http4s.Method.GET

class CodaHaleMetricsSpec extends Http4sSpec {

  val httpClient = Client.fromHttpApp[IO](RemoteEndpointStub.mockEndpoints)

  "A http client with a codehale metrics middleware" should {

    "register a successful 2xx response" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp = serviceClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "client.default.2xx-responses") must beEqualTo(1)
      count(registry, "client.default.active-requests") must beEqualTo(0)
    }

    "register a failed 4xx response" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("badrequest").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, "client.default.4xx-responses") must beEqualTo(1)
      count(registry, "client.default.active-requests") must beEqualTo(0)
    }

    "register a failed 5xx response" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("servererror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, "client.default.5xx-responses") must beEqualTo(1)
      count(registry, "client.default.active-requests") must beEqualTo(0)
    }

    "register a client error" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("clienterror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, "client.default.errors") must beEqualTo(1)
      count(registry, "client.default.active-requests") must beEqualTo(0)
    }

    "register a timeout" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("timeout").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[TimeoutException]
      }
      count(registry, "client.default.timeouts") must beEqualTo(1)
      count(registry, "client.default.active-requests") must beEqualTo(0)
    }

  }

  def count(registry: MetricRegistry, name: String): Long = registry.getCounters.get(name).getCount
}

object RemoteEndpointStub {

  val mockEndpoints = HttpApp[IO] {
    case GET -> Root / "badrequest" =>
      BadRequest("400 Bad Request")
    case GET -> Root / "servererror" =>
      InternalServerError("500 Internal Server Error")
    case GET -> Root / "ok" =>
      Ok("200 OK")
    case GET -> Root / "clienterror" =>
      IO.raiseError[Response[IO]](new IOException("connection error"))
    case GET -> Root / "timeout" =>
      IO.raiseError[Response[IO]](new TimeoutException("request timed out"))
    case _ =>
      NotFound("404 Not Found")
  }
}

