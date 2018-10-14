package org.http4s.metrics.dropwizard

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import org.http4s.{Http4sSpec, HttpRoutes, Request, Status}
import org.http4s.dsl.io._
import org.http4s.metrics.dropwizard.util._
import org.http4s.server.HttpMiddleware
import org.http4s.server.middleware.Metrics

class DropwizardServerMetricsSpec extends Http4sSpec {

  "Http routes with a dropwizard metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
      val withMetrics = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/bad-request"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.BadRequest)
      resp must haveBody("400 Bad Request")
      count(registry, Timer("server.default.4xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.4xx-responses")) must beSome(Array(100000000L))
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/internal-server-error"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.InternalServerError)
      resp must haveBody("500 Internal Server Error")
      count(registry, Timer("server.default.5xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.5xx-responses")) must beSome(Array(100000000L))
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.get-requests")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = POST, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.post-requests")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.post-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = PUT, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.put-requests")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.put-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = DELETE, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.delete-requests")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.delete-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/error"))

      val resp = meteredRoutes.orNotFound(req).attempt.unsafeRunSync

      resp must beLeft
      count(registry, Timer("server.default.errors")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
    }

    "register an abnormal termination" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test9")
      val withMetrics: HttpMiddleware[IO] = Metrics[IO](Dropwizard(registry, "server"))
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/abnormal-termination"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp.body.attempt.compile.lastOrError.unsafeRunSync must beLeft
      count(registry, Timer("server.default.abnormal-terminations")) must beEqualTo(1)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val classifierFunc = (r: Request[IO]) => Some("classifier")
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test10")
      val withMetrics = Metrics[IO](ops = Dropwizard(registry, "server"), classifierF = classifierFunc)
      val meteredRoutes = withMetrics(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.classifier.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("server.classifier.active-requests")) must beEqualTo(0)
      count(registry, Timer("server.classifier.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("server.classifier.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.classifier.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.classifier.2xx-responses")) must beSome(Array(100000000L))
    }
  }

  def testRoutes = HttpRoutes.of[IO](stub)

}
