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
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.http4s.{Http4sSuite, HttpApp, Request, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.middleware.Metrics
import org.http4s.dsl.io._
import org.http4s.metrics.prometheus.util._

class PrometheusClientMetricsSuite extends Http4sSuite {
  private val client: Client[IO] = Client.fromHttpApp[IO](HttpApp[IO](stub))

  private val commonPrefix = Option("client")

  private val customMetricsSettings =
    SyncIO.fromEither(
      PrometheusMetricsNames(
        responseDuration = "request_latency_seconds",
        activeRequests = "active_request_total",
        requests = "request_total",
        abnormalTerminations = "failed_requests"
      ).map(PrometheusMetricsSettings.DefaultSettings.withMetricsNames)
    )

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a 2xx response") {
    case (registry, _, client) =>
      client.expect[String]("/ok").attempt.map { resp =>
        assertEquals(count(registry, "2xx_responses", commonPrefix), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.1)
        assertEquals(resp, Right("200 OK"))
      }
  }

  customMetricsSettings.flatMap(settings => meteredClient(settings = settings)).test(
    "A http client with Prometheus metrics (with custom names) middleware should register a 2xx response") {
    case (registry, settings, client) =>
      client.expect[String]("/ok").attempt.map { resp =>
        assertEquals(count(registry, "2xx_responses", prefix = Option.empty, metricsSettings = settings), 1.0)
        assertEquals(count(registry, "active_requests", prefix = Option.empty, metricsSettings = settings), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", prefix = Option.empty, metricsSettings = settings), 0.05)
        assertEquals(count(registry, "2xx_total_duration", prefix = Option.empty, metricsSettings = settings), 0.1)
        assertEquals(resp, Right("200 OK"))
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a 4xx response") {
    case (registry, _, client) =>
      client.expect[String]("/bad-request").attempt.map { resp =>
        assertEquals(count(registry, "4xx_responses", commonPrefix), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "4xx_headers_duration", commonPrefix), 0.05)
        assertEquals(count(registry, "4xx_total_duration", commonPrefix), 0.1)
        assert(resp match {
          case Left(UnexpectedStatus(Status.BadRequest, _, _)) => true
          case _                                               => false
        })
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a 5xx response") {
    case (registry, _, client) =>
      client.expect[String]("/internal-server-error").attempt.map { resp =>
        assertEquals(count(registry, "5xx_responses", commonPrefix), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "5xx_headers_duration", commonPrefix), 0.05)
        assertEquals(count(registry, "5xx_total_duration", commonPrefix), 0.1)
        assert(resp match {
          case Left(UnexpectedStatus(Status.InternalServerError, _, _)) => true
          case _                                                        => false
        })
      }
  }

  customMetricsSettings.flatMap(settings => meteredClient(settings = settings)).test(
    "A http client with Prometheus metrics (with custom names) middleware should register a 5xx response") {
    case (registry, settings, client) =>
      client.expect[String]("/internal-server-error").attempt.map { resp =>
        assertEquals(count(registry, "5xx_responses", prefix = Option.empty, metricsSettings = settings), 1.0)
        assertEquals(count(registry, "active_requests", prefix = Option.empty, metricsSettings = settings), 0.0)
        assertEquals(count(registry, "5xx_headers_duration", prefix = Option.empty, metricsSettings = settings), 0.05)
        assertEquals(count(registry, "5xx_total_duration", prefix = Option.empty, metricsSettings = settings), 0.1)
        assert(resp match {
          case Left(UnexpectedStatus(Status.InternalServerError, _, _)) => true
          case _                                                        => false
        })
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a GET request") {
    case (registry, _, client) =>
      client.expect[String]("/ok").attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", commonPrefix), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.1)
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a POST request") {
    case (registry, _, client) =>
      client.expect[String](Request[IO](POST, Uri.unsafeFromString("/ok"))).attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", commonPrefix, "post"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix, "post"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "post"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix, "post"), 0.1)
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a PUT request") {
    case (registry, _, client) =>
      client.expect[String](Request[IO](PUT, Uri.unsafeFromString("/ok"))).attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", commonPrefix, "put"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix, "put"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "put"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix, "put"), 0.1)
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a DELETE request") {
    case (registry, _, client) =>
      client.expect[String](Request[IO](DELETE, Uri.unsafeFromString("/ok"))).attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", commonPrefix, "delete"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix, "delete"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "delete"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix, "delete"), 0.1)
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register an error") {
    case (registry, _, client) =>
      client.expect[String]("/error").attempt.map { resp =>
        assert(resp match {
          case Left(_: IOException) => true
          case _                    => false
        })

        assertEquals(count(registry, "errors", commonPrefix, cause = "java.io.IOException"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
      }
  }

  meteredClient(prefix = commonPrefix).test(
    "A http client with Prometheus metrics (with the default names) middleware should register a timeout") {
    case (registry, _, client) =>
      client.expect[String]("/timeout").attempt.map { resp =>
        assert(resp match {
          case Left(_: TimeoutException) => true
          case _                         => false
        })

        assertEquals(count(registry, "timeouts", commonPrefix), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
      }
  }

  private val classifier = (_: Request[IO]) => Some("classifier")

  meteredClient(prefix = commonPrefix, classifier = classifier).test("use the provided request classifier") {
    case (registry, _, client) =>
      client.expect[String]("/ok").attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", commonPrefix, "get", "classifier"), 1.0)
        assertEquals(count(registry, "active_requests", commonPrefix, "get", "classifier"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix, "get", "classifier"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix, "get", "classifier"), 0.1)
      }
  }

  // This tests can't be easily done in munit-cats-effect as it wants to test after the Resource is freed
  meteredClient(prefix = commonPrefix).test("unregister collectors".ignore) {
    case (cr, _, client) =>
      client.expect[String]("/ok").as(cr).map { registry =>
        assertEquals(count(registry, "2xx_responses", commonPrefix), 0.0)
        assertEquals(count(registry, "active_requests", commonPrefix), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", commonPrefix), 0.0)
        assertEquals(count(registry, "2xx_total_duration", commonPrefix), 0.0)
      }
  }

  private def buildMeteredClient(
      prefix: Option[String],
      settings: PrometheusMetricsSettings,
      classifier: Request[IO] => Option[String]
  ) = {
    implicit val clock: Clock[IO] = FakeClock[IO]

    for {
      registry <- Prometheus.collectorRegistry[IO]

      metricsOps <- prefix.fold(
        Prometheus.metricsOps[IO](registry, settings)
      )(Prometheus.metricsOps[IO](registry, _))

      metrics = Metrics(metricsOps, classifier)(client)
    } yield (registry, settings, metrics)
  }

  private def meteredClient(
      prefix: Option[String] = Option.empty,
      settings: PrometheusMetricsSettings = PrometheusMetricsSettings.DefaultSettings,
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ) = ResourceFixture(buildMeteredClient(prefix, settings, classifier))
}
