/*
 * Copyright 2018 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.metrics.prometheus

import cats.effect._
import org.http4s.{Http4sSuite, HttpRoutes, Request, Status}
import org.http4s.Method.GET
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.metrics.prometheus.util._
import org.http4s.server.middleware.Metrics

class PrometheusServerMetricsSuite extends Http4sSuite {

  private val testRoutes = HttpRoutes.of[IO](stub)

  private val commonPrefix = Option("server")

  private val customMetricsSettings =
    SyncIO.fromEither(
      PrometheusMetricsNames(
        responseDuration = "response_latency_seconds",
        activeRequests = "active_request_total",
        requests = "request_total",
        abnormalTerminations = "failed_requests"
      ).map(PrometheusMetricsSettings.DefaultSettings.withMetricsNames)
    )

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a 2xx response") {
    case (registry, _, routes) =>
      val req = Request[IO](uri = uri"/ok")

      val resp = routes.run(req)
      resp.flatMap { r =>
        r.as[String].map { b =>
          assertEquals(b, "200 OK")
          assertEquals(r.status, Status.Ok)
          assertEquals(count(registry, "2xx_responses", commonPrefix), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.05)
          assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.1)
        }
      }
  }

  customMetricsSettings
    .flatMap(settings => meteredRoutes(settings = settings))
    .test(
      "A http routes with Prometheus metrics (with custom names) middleware should register a 2xx response") {
      case (registry, settings, routes) =>
        val req = Request[IO](uri = uri"/ok")

        val resp = routes.run(req)
        resp.flatMap { r =>
          r.as[String].map { b =>
            assertEquals(b, "200 OK")
            assertEquals(r.status, Status.Ok)
            assertEquals(
              count(registry, "2xx_responses", prefix = Option.empty, metricsSettings = settings),
              1.0)
            assertEquals(
              count(registry, "active_requests", prefix = Option.empty, metricsSettings = settings),
              0.0)
            assertEquals(
              count(
                registry,
                "2xx_headers_duration",
                prefix = Option.empty,
                metricsSettings = settings),
              0.05)
            assertEquals(
              count(
                registry,
                "2xx_total_duration",
                prefix = Option.empty,
                metricsSettings = settings),
              0.1)
          }
        }
    }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a 4xx response") {
    case (registry, _, routes) =>
      val req = Request[IO](uri = uri"/bad-request")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.BadRequest)
          assertEquals(b, "400 Bad Request")

          assertEquals(count(registry, "4xx_responses", commonPrefix), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
          assertEquals(count(registry, "4xx_headers_duration", commonPrefix), 0.05)
          assertEquals(count(registry, "4xx_total_duration", commonPrefix), 0.1)
        }
      }
  }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a 5xx response") {
    case (registry, _, routes) =>
      val req = Request[IO](uri = uri"/internal-server-error")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.InternalServerError)
          assertEquals(b, "500 Internal Server Error")

          assertEquals(count(registry, "5xx_responses", commonPrefix), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
          assertEquals(count(registry, "5xx_headers_duration", commonPrefix), 0.05)
          assertEquals(count(registry, "5xx_total_duration", commonPrefix), 0.1)
        }
      }
  }

  customMetricsSettings
    .flatMap(settings => meteredRoutes(settings = settings))
    .test(
      "A http routes with Prometheus metrics (with custom names) middleware should register a 5xx response") {
      case (registry, settings, routes) =>
        val req = Request[IO](uri = uri"/internal-server-error")

        routes.run(req).flatMap { r =>
          r.as[String].map { b =>
            assertEquals(r.status, Status.InternalServerError)
            assertEquals(b, "500 Internal Server Error")

            assertEquals(
              count(registry, "5xx_responses", prefix = Option.empty, metricsSettings = settings),
              1.0)
            assertEquals(
              count(registry, "active_requests", prefix = Option.empty, metricsSettings = settings),
              0.0)
            assertEquals(
              count(
                registry,
                "5xx_headers_duration",
                prefix = Option.empty,
                metricsSettings = settings),
              0.05)
            assertEquals(
              count(
                registry,
                "5xx_total_duration",
                prefix = Option.empty,
                metricsSettings = settings),
              0.1)
          }
        }
    }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a GET request") {
    case (registry, _, routes) =>
      val req = Request[IO](method = GET, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", commonPrefix), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.05)
          assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.1)
        }
      }
  }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a POST request") {
    case (registry, _, routes) =>
      val req = Request[IO](method = POST, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", commonPrefix, "post"), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix, "post"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "post"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", commonPrefix, "post"), 0.1)
        }
      }
  }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a PUT request") {
    case (registry, _, routes) =>
      val req = Request[IO](method = PUT, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", commonPrefix, "put"), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix, "put"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "put"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", commonPrefix, "put"), 0.1)
        }
      }
  }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register a DELETE request") {
    case (registry, _, routes) =>
      val req = Request[IO](method = DELETE, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", commonPrefix, "delete"), 1.0)
          assertEquals(count(registry, "active_requests", commonPrefix, "delete"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "delete"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", commonPrefix, "delete"), 0.1)
        }
      }
  }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register an error") {
    case (registry, _, routes) =>
      val req = Request[IO](method = GET, uri = uri"/error")

      routes.run(req).attempt.map { r =>
        assert(r.isLeft)

        assertEquals(count(registry, "errors", commonPrefix, cause = "java.io.IOException"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "5xx_headers_duration", commonPrefix), 0.05)
        assertEquals(count(registry, "5xx_total_duration", commonPrefix), 0.05)
      }
  }

  meteredRoutes(prefix = commonPrefix).test(
    "A http routes with Prometheus metrics (with the default names) middleware should register an abnormal termination") {
    case (registry, _, routes) =>
      val req = Request[IO](method = GET, uri = uri"/abnormal-termination")

      routes.run(req).flatMap { r =>
        r.body.attempt.compile.lastOrError.map { b =>
          assertEquals(r.status, Status.Ok)
          assert(b.isLeft)

          assertEquals(
            count(
              registry,
              "abnormal_terminations",
              commonPrefix,
              cause = "java.lang.RuntimeException"),
            1.0)
          assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.05)
          assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.1)
        }
      }
  }

  private val classifierFunc = (_: Request[IO]) => Some("classifier")

  meteredRoutes(classifier = classifierFunc, prefix = commonPrefix).test(
    "use the provided request classifier") { case (registry, _, routes) =>
    val req = Request[IO](uri = uri"/ok")

    routes.run(req).flatMap { r =>
      r.as[String].map { b =>
        assertEquals(r.status, Status.Ok)
        assertEquals(b, "200 OK")

        assertEquals(count(registry, "2xx_responses", commonPrefix, "get", "classifier"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix, "get", "classifier"), 0.0)
        assertEquals(
          count(registry, "2xx_headers_duration", commonPrefix, "get", "classifier"),
          0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix, "get", "classifier"), 0.1)
      }
    }
  }

  // This tests can't be easily done in munit-cats-effect as it wants to test after the Resource is freed
  meteredRoutes(prefix = commonPrefix).test("unregister collectors".ignore) {
    case (cr, _, routes) =>
      val req = Request[IO](uri = uri"/ok")

      routes.run(req).as(cr).map { registry =>
        assertEquals(count(registry, "2xx_responses", commonPrefix), 0.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.0)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.0)
      }
  }

  private def buildMeteredRoutes(
      prefix: Option[String],
      settings: PrometheusMetricsSettings,
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ) = {
    implicit val clock: Clock[IO] = FakeClock[IO]

    for {
      registry <- Prometheus.collectorRegistry[IO]

      metricsOps <- prefix.fold(
        Prometheus.metricsOps[IO](registry, settings)
      )(Prometheus.metricsOps[IO](registry, _))

      metrics = Metrics(metricsOps, classifierF = classifier)(testRoutes).orNotFound
    } yield (registry, settings, metrics)
  }

  private def meteredRoutes(
      prefix: Option[String] = Option.empty,
      settings: PrometheusMetricsSettings = PrometheusMetricsSettings.DefaultSettings,
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ) = ResourceFixture(buildMeteredRoutes(prefix, settings, classifier))
}
