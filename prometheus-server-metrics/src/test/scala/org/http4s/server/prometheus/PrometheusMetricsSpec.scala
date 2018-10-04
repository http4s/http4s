package org.http4s.server.prometheus

import cats.effect.IO
import fs2.Stream
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import org.http4s.{Http4sSpec, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io._

class PrometheusMetricsSpec extends Http4sSpec {

  "Http routes with a prometheus metrics middleware" should {

    "register a 2xx response" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a 4xx response" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/bad-request"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.BadRequest)
      resp must haveBody("400 Bad Request")
      count(registry, "4xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a 5xx response" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/internal-server-error"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.InternalServerError)
      resp must haveBody("500 Internal Server Error")
      count(registry, "5xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a GET request" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "get") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a POST request" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = POST, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "post") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a PUT request" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = PUT, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "put") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a DELETE request" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = DELETE, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, "2xx_responses", "delete") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a server error" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/service-error"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).attempt.unsafeRunSync

      resp must beLeft
      count(registry, "server_errors") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register an abnormal termination" in {
      val registry: CollectorRegistry = new CollectorRegistry()
      val withMetrics = PrometheusMetrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/abnormal-termination"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp.body.attempt.compile.lastOrError.unsafeRunSync must beLeft
      count(registry, "abnormal_terminations") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
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

//  def count(registry: MetricRegistry, counter: Counter): Long = registry.getCounters.get(counter.value).getCount
//  def count(registry: MetricRegistry, timer: Timer): Long = registry.getTimers.get(timer.value).getCount

  def count(registry: CollectorRegistry, name: String, method: String = "get"): Double =
    name match {
      case "active_requests" =>
        registry.getSampleValue("server_active_request_count", Array(), Array())
      case "2xx_responses" =>
        registry
          .getSampleValue("server_response_total", Array("method", "code"), Array(method, "2xx"))
      case "4xx_responses" =>
        registry
          .getSampleValue("server_response_total", Array("method", "code"), Array(method, "4xx"))
      case "5xx_responses" =>
        registry
          .getSampleValue("server_response_total", Array("method", "code"), Array(method, "5xx"))
      case "server_errors" =>
        registry.getSampleValue(
          "server_abnormal_terminations_total",
          Array("termination_type"),
          Array("server_error"))
      case "abnormal_terminations" =>
        registry.getSampleValue(
          "server_abnormal_terminations_total",
          Array("termination_type"),
          Array("abnormal_termination"))
    }
}
