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
import org.http4s.{Http4sSuite, HttpApp, HttpRoutes, Request, Status}
import org.http4s.Method.GET
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.metrics.prometheus.util._
import org.http4s.server.middleware.Metrics

class PrometheusServerMetricsSuite extends Http4sSuite {

  private val testRoutes = HttpRoutes.of[IO](stub)

  // "A http routes with a prometheus metrics middleware" should {
  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a 2xx response") {
    case (registry, routes) =>
      val req = Request[IO](uri = uri"/ok")

      val resp = routes.run(req)
      resp.flatMap { r =>
        r.as[String].map { b =>
          assertEquals(b, "200 OK")
          assertEquals(r.status, Status.Ok)
          assertEquals(count(registry, "2xx_responses", "server"), 1.0)
          assertEquals(count(registry, "active_requests", "server"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a 4xx response") {
    case (registry, routes) =>
      val req = Request[IO](uri = uri"/bad-request")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.BadRequest)
          assertEquals(b, "400 Bad Request")

          assertEquals(count(registry, "4xx_responses", "server"), 1.0)
          assertEquals(count(registry, "active_requests", "server"), 0.0)
          assertEquals(count(registry, "4xx_headers_duration", "server"), 0.05)
          assertEquals(count(registry, "4xx_total_duration", "server"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a 5xx response") {
    case (registry, routes) =>
      val req = Request[IO](uri = uri"/internal-server-error")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.InternalServerError)
          assertEquals(b, "500 Internal Server Error")

          assertEquals(count(registry, "5xx_responses", "server"), 1.0)
          assertEquals(count(registry, "active_requests", "server"), 0.0)
          assertEquals(count(registry, "5xx_headers_duration", "server"), 0.05)
          assertEquals(count(registry, "5xx_total_duration", "server"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a GET request") {
    case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", "server", "get"), 1.0)
          assertEquals(count(registry, "active_requests", "server", "get"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server", "get"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server", "get"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a POST request") {
    case (registry, routes) =>
      val req = Request[IO](method = POST, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", "server", "post"), 1.0)
          assertEquals(count(registry, "active_requests", "server", "post"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server", "post"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server", "post"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a PUT request") {
    case (registry, routes) =>
      val req = Request[IO](method = PUT, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", "server", "put"), 1.0)
          assertEquals(count(registry, "active_requests", "server", "put"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server", "put"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server", "put"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register a DELETE request") {
    case (registry, routes) =>
      val req = Request[IO](method = DELETE, uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", "server", "delete"), 1.0)
          assertEquals(count(registry, "active_requests", "server", "delete"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server", "delete"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server", "delete"), 0.1)
        }
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register an error") {
    case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/error")

      routes.run(req).attempt.map { r =>
        assert(r.isLeft)

        assertEquals(count(registry, "errors", "server"), 1.0)
        assertEquals(count(registry, "active_requests", "server"), 0.0)
        assertEquals(count(registry, "5xx_headers_duration", "server"), 0.05)
        assertEquals(count(registry, "5xx_total_duration", "server"), 0.05)
      }
  }

  meteredRoutes().test(
    "A http routes with a prometheus metrics middleware should register an abnormal termination") {
    case (registry, routes) =>
      val req = Request[IO](method = GET, uri = uri"/abnormal-termination")

      routes.run(req).flatMap { r =>
        r.body.attempt.compile.lastOrError.map { b =>
          assertEquals(r.status, Status.Ok)
          assert(b.isLeft)

          assertEquals(count(registry, "abnormal_terminations", "server"), 1.0)
          assertEquals(count(registry, "active_requests", "server"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server"), 0.1)
        }
      }
  }

  val classifierFunc = (_: Request[IO]) => Some("classifier")
  meteredRoutes(classifierFunc).test("use the provided request classifier") {
    case (registry, routes) =>
      val req = Request[IO](uri = uri"/ok")

      routes.run(req).flatMap { r =>
        r.as[String].map { b =>
          assertEquals(r.status, Status.Ok)
          assertEquals(b, "200 OK")

          assertEquals(count(registry, "2xx_responses", "server", "get", "classifier"), 1.0)
          assertEquals(count(registry, "active_requests", "server", "get", "classifier"), 0.0)
          assertEquals(count(registry, "2xx_headers_duration", "server", "get", "classifier"), 0.05)
          assertEquals(count(registry, "2xx_total_duration", "server", "get", "classifier"), 0.1)
        }
      }
  }

  // This tests can't be easily done in munit-cats-effect as it wants to test after the Resource is freed
  meteredRoutes().test("unregister collectors".ignore) { case (cr, routes) =>
    val req = Request[IO](uri = uri"/ok")

    routes.run(req).as(cr).map { registry =>
      assertEquals(count(registry, "2xx_responses", "server"), 0.0)
      assertEquals(count(registry, "active_requests", "server"), 0.0)
      assertEquals(count(registry, "2xx_headers_duration", "server"), 0.0)
      assertEquals(count(registry, "2xx_total_duration", "server"), 0.0)
    }
  }

  def buildMeteredRoutes(
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ): Resource[IO, (CollectorRegistry, HttpApp[IO])] = {
    implicit val clock: Clock[IO] = FakeClock[IO]
    for {
      registry <- Prometheus.collectorRegistry[IO]
      metrics <- Prometheus.metricsOps[IO](registry, "server")
    } yield (registry, Metrics(metrics, classifierF = classifier)(testRoutes).orNotFound)
  }

  def meteredRoutes(
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ): FunFixture[(CollectorRegistry, HttpApp[IO])] =
    ResourceFixture(buildMeteredRoutes(classifier))
}
