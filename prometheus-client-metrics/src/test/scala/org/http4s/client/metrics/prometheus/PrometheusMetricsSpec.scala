package org.http4s.client.metrics.prometheus

import cats.effect.{Clock, IO}
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.{TimeUnit, TimeoutException}
import org.http4s.{Http4sSpec, HttpApp, Request, Response, Status}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.metrics.core.Metrics
import org.http4s.dsl.io._
import org.http4s.Method.GET
import scala.concurrent.duration.TimeUnit

class PrometheusMetricsSpec extends Http4sSpec {

  val client = Client.fromHttpApp[IO](RemoteEndpointStub.mockEndpoints)

  "A http client with a prometheus metrics middleware" should {

    "register a successful 2xx response" in {
      implicit val clock = new FakeClock()
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "2xx_headers_duration") must beEqualTo(0.05)
      count(registry, "2xx_total_duration") must beEqualTo(0.1)
    }

    "register a failed 4xx response" in {
      implicit val clock = new FakeClock()
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("badrequest").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, "4xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "4xx_headers_duration") must beEqualTo(0.05)
      count(registry, "4xx_total_duration") must beEqualTo(0.1)
    }

    "register a failed 5xx response" in {
      implicit val clock = new FakeClock()
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("error").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, "5xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "5xx_headers_duration") must beEqualTo(0.05)
      count(registry, "5xx_total_duration") must beEqualTo(0.1)
    }

    "register a client error" in {
      implicit val clock = new FakeClock()
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("clienterror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, "client_errors") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a timeout" in {
      implicit val clock = new FakeClock()
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("timeout").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[TimeoutException]
      }
      count(registry, "timeouts") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "use the provided request classifier" in {
      implicit val clock = new FakeClock()
      val requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"), requestMethodClassifier)(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "get") must beEqualTo(1)
      count(registry, "active_requests", "get") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "get") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "get") must beEqualTo(0.1)
    }
  }

  def count(registry: CollectorRegistry, name: String, classifier: String = ""): Double =
    name match {
      case "active_requests" =>
        registry.getSampleValue(
          "client_active_request_count",
          Array("classifier"),
          Array(classifier))
      case "2xx_responses" =>
        registry
          .getSampleValue(
            "client_response_total",
            Array("classifier", "code"),
            Array(classifier, "2xx"))
      case "2xx_headers_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "code", "response_phase"),
          Array(classifier, "2xx", "response_received"))
      case "2xx_total_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "code", "response_phase"),
          Array(classifier, "2xx", "body_processed"))
      case "4xx_responses" =>
        registry
          .getSampleValue(
            "client_response_total",
            Array("classifier", "code"),
            Array(classifier, "4xx"))
      case "4xx_headers_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "code", "response_phase"),
          Array(classifier, "4xx", "response_received"))
      case "4xx_total_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "code", "response_phase"),
          Array(classifier, "4xx", "body_processed"))
      case "5xx_responses" =>
        registry
          .getSampleValue(
            "client_response_total",
            Array("classifier", "code"),
            Array(classifier, "5xx"))
      case "5xx_headers_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "code", "response_phase"),
          Array(classifier, "5xx", "response_received"))
      case "5xx_total_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "code", "response_phase"),
          Array(classifier, "5xx", "body_processed"))
      case "timeouts" =>
        registry.getSampleValue(
          "client_client_timeouts_total",
          Array("classifier"),
          Array(classifier))
      case "client_errors" =>
        registry.getSampleValue(
          "client_client_errors_total",
          Array("classifier"),
          Array(classifier))
    }
}

class FakeClock extends Clock[IO] {
  private var count = 0L

  override def realTime(unit: TimeUnit): IO[Long] = {
    count += 50
    IO(unit.convert(count, TimeUnit.MILLISECONDS))
  }

  override def monotonic(unit: TimeUnit): IO[Long] = {
    count += 50
    IO(unit.convert(count, TimeUnit.MILLISECONDS))
  }
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
