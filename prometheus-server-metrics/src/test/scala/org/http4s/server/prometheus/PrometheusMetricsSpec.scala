package org.http4s.server.prometheus

import cats.effect.{Clock, IO, Sync}
import fs2.Stream
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.http4s.{Http4sSpec, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io._
import org.http4s.server.HttpMiddleware
import org.http4s.server.middleware.Metrics
import scala.concurrent.duration.TimeUnit

class PrometheusMetricsSpec extends Http4sSpec {

  "Http routes with a prometheus metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "2xx_headers_duration") must beEqualTo(0.05)
      count(registry, "2xx_total_duration") must beEqualTo(0.1)
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/bad-request"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.BadRequest)
      resp must haveBody("400 Bad Request")
      count(registry, "4xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "4xx_headers_duration") must beEqualTo(0.05)
      count(registry, "4xx_total_duration") must beEqualTo(0.1)
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/internal-server-error"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.InternalServerError)
      resp must haveBody("500 Internal Server Error")
      count(registry, "5xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "5xx_headers_duration") must beEqualTo(0.05)
      count(registry, "5xx_total_duration") must beEqualTo(0.1)
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "get") must beEqualTo(1)
      count(registry, "active_requests", "get") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "get") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "get") must beEqualTo(0.1)
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = POST, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "post") must beEqualTo(1)
      count(registry, "active_requests", "post") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "post") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "post") must beEqualTo(0.1)
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = PUT, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "put") must beEqualTo(1)
      count(registry, "active_requests", "put") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "put") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "put") must beEqualTo(0.1)
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = DELETE, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "delete") must beEqualTo(1)
      count(registry, "active_requests", "delete") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "delete") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "delete") must beEqualTo(0.1)
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/service-error"))

      val resp = meteredRoutes.orNotFound(req).attempt.unsafeRunSync

      resp must beLeft
      count(registry, "server_errors") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "5xx_headers_duration") must beEqualTo(0.05)
      count(registry, "5xx_total_duration") must beEqualTo(0.1)
    }

    "register an abnormal termination" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Prometheus(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/abnormal-termination"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp.body.attempt.compile.lastOrError.unsafeRunSync must beLeft
      count(registry, "abnormal_terminations") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "2xx_headers_duration") must beEqualTo(0.05)
      count(registry, "2xx_total_duration") must beEqualTo(0.1)
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val classifierFunc = (_: Request[IO]) => Some("classifier")
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](ops = Prometheus(registry, "server"), classifierF = classifierFunc)
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "get", "classifier") must beEqualTo(1)
      count(registry, "active_requests", "get", "classifier") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "get", "classifier") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "get", "classifier") must beEqualTo(0.1)
    }
  }

  def testRoutes =
    HttpRoutes.of[IO] {
      case (GET | POST | PUT | DELETE) -> Root / "ok" =>
        Ok("200 Ok")
      case GET -> Root / "bad-request" =>
        BadRequest("400 Bad Request")
      case GET -> Root / "internal-server-error" =>
        InternalServerError("500 Internal Server Error")
      case GET -> Root / "service-error" =>
        IO.raiseError[Response[IO]](new IOException("service error"))
      case GET -> Root / "abnormal-termination" =>
        Ok("200 Ok").map(
          _.withBodyStream(Stream.raiseError[IO](new RuntimeException("Abnormal termination"))))
      case _ =>
        NotFound("404 Not Found")
    }

  def count(registry: CollectorRegistry, name: String, method: String = "get", classifier: String = ""): Double =
    name match {
      case "active_requests" =>
        registry.getSampleValue(
          "server_active_request_count",
          Array("classifier"),
          Array(classifier))
      case "2xx_responses" =>
        registry
          .getSampleValue(
            "server_response_total",
            Array("classifier", "method", "status"),
            Array(classifier, method, "2xx"))
      case "2xx_headers_duration" =>
        registry.getSampleValue(
          "server_response_duration_seconds_sum",
          Array("classifier", "method", "serving_phase"),
          Array(classifier, method, "header_phase"))
      case "2xx_total_duration" =>
        registry.getSampleValue(
          "server_response_duration_seconds_sum",
          Array("classifier", "method", "serving_phase"),
          Array(classifier, method, "body_phase"))
      case "4xx_responses" =>
        registry
          .getSampleValue(
            "server_response_total",
            Array("classifier", "method", "status"),
            Array(classifier, method, "4xx"))
      case "4xx_headers_duration" =>
        registry.getSampleValue(
          "server_response_duration_seconds_sum",
          Array("classifier", "method", "serving_phase"),
          Array(classifier, method, "header_phase"))
      case "4xx_total_duration" =>
        registry.getSampleValue(
          "server_response_duration_seconds_sum",
          Array("classifier", "method", "serving_phase"),
          Array(classifier, method, "body_phase"))
      case "5xx_responses" =>
        registry
          .getSampleValue(
            "server_response_total",
            Array("classifier", "method", "status"),
            Array(classifier, method, "5xx"))
      case "5xx_headers_duration" =>
        registry.getSampleValue(
          "server_response_duration_seconds_sum",
          Array("classifier", "method", "serving_phase"),
          Array(classifier, method, "header_phase"))
      case "5xx_total_duration" =>
        registry.getSampleValue(
          "server_response_duration_seconds_sum",
          Array("classifier", "method", "serving_phase"),
          Array(classifier, method, "body_phase"))
      case "server_errors" =>
        registry.getSampleValue(
          "server_abnormal_terminations_count",
          Array("classifier", "termination_type"),
          Array(classifier, "server_error"))
      case "abnormal_terminations" =>
        registry.getSampleValue(
          "server_abnormal_terminations_count",
          Array("classifier", "termination_type"),
          Array(classifier, "abnormal_termination"))
    }

  object FakeClock {
    def apply[F[_]: Sync] = new Clock[F] {
      private var count = 0L

      override def realTime(unit: TimeUnit): F[Long] = {
        count += 50
        Sync[F].delay(unit.convert(count, TimeUnit.MILLISECONDS))
      }

      override def monotonic(unit: TimeUnit): F[Long] = {
        count += 50
        Sync[F].delay(unit.convert(count, TimeUnit.MILLISECONDS))
      }
    }
  }

}
