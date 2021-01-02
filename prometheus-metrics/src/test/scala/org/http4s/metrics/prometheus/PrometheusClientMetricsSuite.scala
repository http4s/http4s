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

import cats.effect.{Clock, IO, Resource}
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.http4s.{Http4sSuite, HttpApp, Request, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.middleware.Metrics
import org.http4s.dsl.io._
import org.http4s.metrics.prometheus.util._

class PrometheusClientMetricsSuite extends Http4sSuite {
  val client: Client[IO] = Client.fromHttpApp[IO](HttpApp[IO](stub))

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a 2xx response") {
    case (registry, client) =>
      client.expect[String]("ok").attempt.map { resp =>
        assertEquals(count(registry, "2xx_responses", "client"), 1.0)
        assertEquals(count(registry, "active_requests", "client"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", "client"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", "client"), 0.1)
        assertEquals(resp, Right("200 OK"))
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a 4xx response") {
    case (registry, client) =>
      client.expect[String]("bad-request").attempt.map { resp =>
        assertEquals(count(registry, "4xx_responses", "client"), 1.0)
        assertEquals(count(registry, "active_requests", "client"), 0.0)
        assertEquals(count(registry, "4xx_headers_duration", "client"), 0.05)
        assertEquals(count(registry, "4xx_total_duration", "client"), 0.1)
        assert(resp match {
          case Left(UnexpectedStatus(Status.BadRequest)) => true
          case _ => false
        })
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a 5xx response") {
    case (registry, client) =>
      client.expect[String]("internal-server-error").attempt.map { resp =>
        assertEquals(count(registry, "5xx_responses", "client"), 1.0)
        assertEquals(count(registry, "active_requests", "client"), 0.0)
        assertEquals(count(registry, "5xx_headers_duration", "client"), 0.05)
        assertEquals(count(registry, "5xx_total_duration", "client"), 0.1)
        assert(resp match {
          case Left(UnexpectedStatus(Status.InternalServerError)) => true
          case _ => false
        })
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a GET request") {
    case (registry, client) =>
      client.expect[String]("ok").attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", "client", "get"), 1.0)
        assertEquals(count(registry, "active_requests", "client", "get"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", "client", "get"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", "client", "get"), 0.1)
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a POST request") {
    case (registry, client) =>
      client.expect[String](Request[IO](POST, Uri.unsafeFromString("ok"))).attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", "client", "post"), 1.0)
        assertEquals(count(registry, "active_requests", "client", "post"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", "client", "post"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", "client", "post"), 0.1)
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a PUT request") {
    case (registry, client) =>
      client.expect[String](Request[IO](PUT, Uri.unsafeFromString("ok"))).attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", "client", "put"), 1.0)
        assertEquals(count(registry, "active_requests", "client", "put"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", "client", "put"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", "client", "put"), 0.1)
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a DELETE request") {
    case (registry, client) =>
      client.expect[String](Request[IO](DELETE, Uri.unsafeFromString("ok"))).attempt.map { resp =>
        assertEquals(resp, Right("200 OK"))

        assertEquals(count(registry, "2xx_responses", "client", "delete"), 1.0)
        assertEquals(count(registry, "active_requests", "client", "delete"), 0.0)
        assertEquals(count(registry, "2xx_headers_duration", "client", "delete"), 0.05)
        assertEquals(count(registry, "2xx_total_duration", "client", "delete"), 0.1)
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register an error") {
    case (registry, client) =>
      client.expect[String]("error").attempt.map { resp =>
        assert(resp match {
          case Left(_: IOException) => true
          case _ => false
        })

        assertEquals(count(registry, "errors", "client"), 1.0)
        assertEquals(count(registry, "active_requests", "client"), 0.0)
      }
  }

  meteredClient().test(
    "A http client with a prometheus metrics middleware should register a timeout") {
    case (registry, client) =>
      client.expect[String]("timeout").attempt.map { resp =>
        assert(resp match {
          case Left(_: TimeoutException) => true
          case _ => false
        })

        assertEquals(count(registry, "timeouts", "client"), 1.0)
        assertEquals(count(registry, "active_requests", "client"), 0.0)
      }
  }

  val classifier = (_: Request[IO]) => Some("classifier")
  meteredClient(classifier).test("use the provided request classifier") { case (registry, client) =>
    client.expect[String]("ok").attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))

      assertEquals(count(registry, "2xx_responses", "client", "get", "classifier"), 1.0)
      assertEquals(count(registry, "active_requests", "client", "get", "classifier"), 0.0)
      assertEquals(count(registry, "2xx_headers_duration", "client", "get", "classifier"), 0.05)
      assertEquals(count(registry, "2xx_total_duration", "client", "get", "classifier"), 0.1)
    }
  }

  // This tests can't be easily done in munit-cats-effect as it wants to test after the Resource is freed
  meteredClient().test("unregister collectors".ignore) { case (cr, client) =>
    client.expect[String]("ok").as(cr).map { registry =>
      assertEquals(count(registry, "2xx_responses", "client"), 0.0)
      assertEquals(count(registry, "active_requests", "client"), 0.0)
      assertEquals(count(registry, "2xx_headers_duration", "client"), 0.0)
      assertEquals(count(registry, "2xx_total_duration", "client"), 0.0)
    }
  }

  private def buildMeteredClient(
      classifier: Request[IO] => Option[String]
  ): Resource[IO, (CollectorRegistry, Client[IO])] = {
    implicit val clock: Clock[IO] = FakeClock[IO]

    for {
      registry <- Prometheus.collectorRegistry[IO]
      metrics <- Prometheus.metricsOps[IO](registry, "client")
    } yield (registry, Metrics(metrics, classifier)(client))
  }

  def meteredClient(
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ): FunFixture[(CollectorRegistry, Client[IO])] =
    ResourceFixture(buildMeteredClient(classifier))
}
