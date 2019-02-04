package org.http4s.metrics.prometheus

import cats.effect.IO
import io.prometheus.client.CollectorRegistry
import org.http4s.{Http4sSpec, HttpRoutes, Request, Status}
import org.http4s.dsl.io._
import org.http4s.metrics.prometheus.util._
import org.http4s.server.middleware.Metrics
import org.http4s.Method.GET
import org.http4s.Uri.uri

class PrometheusServerMetricsSpec extends Http4sSpec {

  "A http routes with a prometheus metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, "2xx_responses", "server") must beEqualTo(1)
      count(registry, "active_requests", "server") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server") must beEqualTo(0.1)
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](uri = uri("/bad-request"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.BadRequest)
      resp must haveBody("400 Bad Request")
      count(registry, "4xx_responses", "server") must beEqualTo(1)
      count(registry, "active_requests", "server") must beEqualTo(0)
      count(registry, "4xx_headers_duration", "server") must beEqualTo(0.05)
      count(registry, "4xx_total_duration", "server") must beEqualTo(0.1)
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](uri = uri("/internal-server-error"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.InternalServerError)
      resp must haveBody("500 Internal Server Error")
      count(registry, "5xx_responses", "server") must beEqualTo(1)
      count(registry, "active_requests", "server") must beEqualTo(0)
      count(registry, "5xx_headers_duration", "server") must beEqualTo(0.05)
      count(registry, "5xx_total_duration", "server") must beEqualTo(0.1)
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](method = GET, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, "2xx_responses", "server", "get") must beEqualTo(1)
      count(registry, "active_requests", "server", "get") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server", "get") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server", "get") must beEqualTo(0.1)
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](method = POST, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, "2xx_responses", "server", "post") must beEqualTo(1)
      count(registry, "active_requests", "server", "post") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server", "post") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server", "post") must beEqualTo(0.1)
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](method = PUT, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, "2xx_responses", "server", "put") must beEqualTo(1)
      count(registry, "active_requests", "server", "put") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server", "put") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server", "put") must beEqualTo(0.1)
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](method = DELETE, uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, "2xx_responses", "server", "delete") must beEqualTo(1)
      count(registry, "active_requests", "server", "delete") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server", "delete") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server", "delete") must beEqualTo(0.1)
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](method = GET, uri = uri("/error"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).attempt.unsafeRunSync

      resp must beLeft
      count(registry, "errors", "server") must beEqualTo(1)
      count(registry, "active_requests", "server") must beEqualTo(0)
      count(registry, "5xx_headers_duration", "server") must beEqualTo(0.05)
      count(registry, "5xx_total_duration", "server") must beEqualTo(0.1)
    }

    "register an abnormal termination" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(Metrics(_)(testRoutes))
      val req = Request[IO](method = GET, uri = uri("/abnormal-termination"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp.body.attempt.compile.lastOrError.unsafeRunSync must beLeft
      count(registry, "abnormal_terminations", "server") must beEqualTo(1)
      count(registry, "active_requests", "server") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server") must beEqualTo(0.1)
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val classifierFunc = (_: Request[IO]) => Some("classifier")
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredRoutes = Prometheus(registry, "server").map(op =>
        Metrics[IO](ops = op, classifierF = classifierFunc)(testRoutes))
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.flatMap(_.orNotFound(req)).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, "2xx_responses", "server", "get", "classifier") must beEqualTo(1)
      count(registry, "active_requests", "server", "get", "classifier") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server", "get", "classifier") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "server", "get", "classifier") must beEqualTo(0.1)
    }
  }

  private def testRoutes = HttpRoutes.of[IO](stub)

}
