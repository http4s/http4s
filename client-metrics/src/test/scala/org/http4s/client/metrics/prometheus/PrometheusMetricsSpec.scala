package org.http4s.client.metrics.prometheus

import cats.effect.IO
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.http4s.{Http4sSpec, HttpApp, Response, Status}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.metrics.core.Metrics
import org.http4s.client.metrics.prometheus.PrometheusOps._
import org.http4s.dsl.io._
import org.http4s.Method.GET

class PrometheusMetricsSpec extends Http4sSpec {

  val httpClient = Client.fromHttpApp[IO](RemoteEndpointStub.mockEndpoints)

  "A http client with a prometheus metrics middleware" should {

    "register a successful 2xx response" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp = serviceClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a failed 4xx response" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("badrequest").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, "4xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a failed 5xx response" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("error").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, "5xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a client error" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("clienterror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, "client_errors") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a timeout" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val serviceClient = Metrics(registry, "client")(httpClient)

      val resp =
        serviceClient.expect[String]("timeout").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[TimeoutException]
      }
      count(registry, "timeouts") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

  }

  def count(registry: CollectorRegistry, name: String): Double = name match {
    case "active_requests" =>
      registry.getSampleValue("client_active_request_count", Array("destination"), Array(""))
    case "2xx_responses" =>
      registry.getSampleValue("client_response_duration_seconds_count",
        Array("destination", "code", "response_phase"), Array("", "2xx", "response_received"))
    case "4xx_responses" =>
      registry.getSampleValue("client_response_duration_seconds_count",
        Array("destination", "code", "response_phase"), Array("", "4xx", "response_received"))
    case "5xx_responses" =>
      registry.getSampleValue("client_response_duration_seconds_count",
        Array("destination", "code", "response_phase"), Array("", "5xx", "response_received"))
    case "timeouts" =>
      registry.getSampleValue("client_client_timeouts_total", Array("destination"), Array(""))
    case "client_errors" =>
      registry.getSampleValue("client_client_errors_total", Array("destination"), Array(""))  }
}

object RemoteEndpointStub {

  val mockEndpoints = HttpApp[IO] {
    case GET -> Root / "badrequest" =>
      BadRequest("400 Bad Request")
    case GET -> Root / "error" =>
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

