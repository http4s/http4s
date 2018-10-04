package org.http4s.server.metrics

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import fs2.Stream
import java.io.IOException
import org.http4s.{Http4sSpec, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io._
import org.http4s.server.HttpMiddleware

class MetricsSpec extends Http4sSpec {

  "Http routes with a dropwizard metrics middleware" should {

    "register a 2xx response" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, Timer("server.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a 4xx response" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/bad-request"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.BadRequest)
      resp must haveBody("400 Bad Request")
      count(registry, Timer("server.4xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a 5xx response" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/internal-server-error"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.InternalServerError)
      resp must haveBody("500 Internal Server Error")
      count(registry, Timer("server.5xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a GET request" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, Timer("server.get-requests")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a POST request" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = POST, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, Timer("server.post-requests")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a PUT request" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = PUT, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, Timer("server.put-requests")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a DELETE request" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = DELETE, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 Ok")
      count(registry, Timer("server.delete-requests")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register a service error" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/service-error"))

      val resp = meteredRoutes.orNotFound(req).attempt.unsafeRunSync

      resp must beLeft
      count(registry, Timer("server.service-errors")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
    }

    "register an abnormal termination" in {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test9")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](registry, "server")
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/abnormal-termination"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp.body.attempt.compile.lastOrError.unsafeRunSync must beLeft
      count(registry, Timer("server.abnormal-terminations")) must beEqualTo(1)
      count(registry, Counter("server.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.requests")) must beEqualTo(1)
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

  def count(registry: MetricRegistry, counter: Counter): Long =
    registry.getCounters.get(counter.value).getCount
  def count(registry: MetricRegistry, timer: Timer): Long =
    registry.getTimers.get(timer.value).getCount
}

case class Counter(value: String)
case class Timer(value: String)
