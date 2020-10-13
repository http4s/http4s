/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.metrics.prometheus

import cats.effect.{Clock, IO, Resource}
import io.prometheus.client.CollectorRegistry
import org.http4s.{Http4sSpec, HttpApp, HttpRoutes, Request, Status}
import org.http4s.Method.GET
import org.http4s.dsl.io._
import org.http4s.metrics.prometheus.util._
import org.http4s.server.middleware.Metrics
import org.http4s.testing.Http4sLegacyMatchersIO
import org.specs2.execute.AsResult
import scala.concurrent.duration._

class PrometheusServerMetricsSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  private val testRoutes = HttpRoutes.of[IO](stub)

  "A http routes with a prometheus metrics middleware" should {
    "register a 2xx response" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](uri = uri"/ok")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.Ok)
        resp must haveBody("200 OK")

        count(registry, "2xx_responses", "server") must beEqualTo(1)
        count(registry, "active_requests", "server") must beEqualTo(0)
        count(registry, "2xx_headers_duration", "server") must beEqualTo(0.05)
        count(registry, "2xx_total_duration", "server") must beEqualTo(0.1)
      }
    }

    "register a 4xx response" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](uri = uri"/bad-request")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.BadRequest)
        resp must haveBody("400 Bad Request")

        count(registry, "4xx_responses", "server") must beEqualTo(1)
        count(registry, "active_requests", "server") must beEqualTo(0)
        count(registry, "4xx_headers_duration", "server") must beEqualTo(0.05)
        count(registry, "4xx_total_duration", "server") must beEqualTo(0.1)
      }
    }

    "register a 5xx response" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](uri = uri"/internal-server-error")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.InternalServerError)
        resp must haveBody("500 Internal Server Error")

        count(registry, "5xx_responses", "server") must beEqualTo(1)
        count(registry, "active_requests", "server") must beEqualTo(0)
        count(registry, "5xx_headers_duration", "server") must beEqualTo(0.05)
        count(registry, "5xx_total_duration", "server") must beEqualTo(0.1)
      }
    }

    "register a GET request" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/ok")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.Ok)
        resp must haveBody("200 OK")

        count(registry, "2xx_responses", "server", "get") must beEqualTo(1)
        count(registry, "active_requests", "server", "get") must beEqualTo(0)
        count(registry, "2xx_headers_duration", "server", "get") must beEqualTo(0.05)
        count(registry, "2xx_total_duration", "server", "get") must beEqualTo(0.1)
      }
    }

    "register a POST request" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = POST, uri = uri"/ok")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.Ok)
        resp must haveBody("200 OK")

        count(registry, "2xx_responses", "server", "post") must beEqualTo(1)
        count(registry, "active_requests", "server", "post") must beEqualTo(0)
        count(registry, "2xx_headers_duration", "server", "post") must beEqualTo(0.05)
        count(registry, "2xx_total_duration", "server", "post") must beEqualTo(0.1)
      }
    }

    "register a PUT request" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = PUT, uri = uri"/ok")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.Ok)
        resp must haveBody("200 OK")

        count(registry, "2xx_responses", "server", "put") must beEqualTo(1)
        count(registry, "active_requests", "server", "put") must beEqualTo(0)
        count(registry, "2xx_headers_duration", "server", "put") must beEqualTo(0.05)
        count(registry, "2xx_total_duration", "server", "put") must beEqualTo(0.1)
      }
    }

    "register a DELETE request" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = DELETE, uri = uri"/ok")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.Ok)
        resp must haveBody("200 OK")

        count(registry, "2xx_responses", "server", "delete") must beEqualTo(1)
        count(registry, "active_requests", "server", "delete") must beEqualTo(0)
        count(registry, "2xx_headers_duration", "server", "delete") must beEqualTo(0.05)
        count(registry, "2xx_total_duration", "server", "delete") must beEqualTo(0.1)
      }
    }

    "register an error" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/error")

      for {
        resp <- routes.run(req).attempt
      } yield {
        resp must beLeft

        count(registry, "errors", "server", cause = "java.io.IOException") must beEqualTo(1)
        count(registry, "active_requests", "server") must beEqualTo(0)
        count(registry, "5xx_headers_duration", "server") must beEqualTo(0.05)
        count(registry, "5xx_total_duration", "server") must beEqualTo(0.1)
      }
    }

    "register a cancel" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/never")

      for {
        resp <- routes.run(req).timeout(10.millis).attempt
      } yield {
        resp must beLeft
        count(registry, "cancels", "server") must beEqualTo(1)
        count(registry, "active_requests", "server") must beEqualTo(0)
      }
    }

    "register an abnormal termination" in withMeteredRoutes { case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/abnormal-termination")

      for {
        resp <- routes.run(req)
      } yield {
        resp must haveStatus(Status.Ok)
        resp.body.attempt.compile.lastOrError.unsafeRunSync() must beLeft

        count(
          registry,
          "abnormal_terminations",
          "server",
          cause = "java.lang.RuntimeException") must beEqualTo(1)
        count(registry, "active_requests", "server") must beEqualTo(0)
        count(registry, "2xx_headers_duration", "server") must beEqualTo(0.05)
        count(registry, "2xx_total_duration", "server") must beEqualTo(0.1)
      }
    }

    "use the provided request classifier" in {
      val classifierFunc = (_: Request[IO]) => Some("classifier")

      val req = Request[IO](uri = uri"/ok")

      meteredRoutes(classifierFunc)
        .use { case (registry, routes) =>
          for {
            resp <- routes.run(req)
          } yield {
            resp must haveStatus(Status.Ok)
            resp must haveBody("200 OK")

            count(registry, "2xx_responses", "server", "get", "classifier") must beEqualTo(1)
            count(registry, "active_requests", "server", "get", "classifier") must beEqualTo(0)
            count(registry, "2xx_headers_duration", "server", "get", "classifier") must beEqualTo(
              0.05)
            count(registry, "2xx_total_duration", "server", "get", "classifier") must beEqualTo(0.1)
          }
        }
        .unsafeRunSync()
    }

    "unregister collectors" in {
      val req = Request[IO](uri = uri"/ok")

      val registry = meteredRoutes()
        .use { case (cr, routes) => routes.run(req).as(cr) }
        .unsafeRunSync()

      count(registry, "2xx_responses", "server") must beEqualTo(0)
      count(registry, "active_requests", "server") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "server") must beEqualTo(0)
      count(registry, "2xx_total_duration", "server") must beEqualTo(0)
    }
  }

  private def withMeteredRoutes[R: AsResult](in: (CollectorRegistry, HttpApp[IO]) => IO[R]): R =
    meteredRoutes().use(in.tupled).unsafeRunSync()

  private def meteredRoutes(
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ): Resource[IO, (CollectorRegistry, HttpApp[IO])] = {
    implicit val clock: Clock[IO] = FakeClock[IO]
    for {
      registry <- Prometheus.collectorRegistry[IO]
      metrics <- Prometheus.metricsOps[IO](registry, "server")
    } yield (registry, Metrics(metrics, classifierF = classifier)(testRoutes).orNotFound)
  }
}
