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

package org.http4s.metrics.dropwizard

import cats.effect.Clock
import cats.effect.IO
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SharedMetricRegistries
import org.http4s.Http4sSuite
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Status
import org.http4s.dsl.io._
import org.http4s.metrics.dropwizard.util._
import org.http4s.server.middleware.Metrics
import org.http4s.syntax.all._

class DropwizardServerMetricsSuite extends Http4sSuite {
  test("register a 2xx response") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](uri = uri"/ok")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(b, "200 OK")
        assertEquals(count(registry, Timer("server.default.2xx-responses")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.get-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.2xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register a 4xx response") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)

    val req = Request[IO](uri = uri"/bad-request")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.BadRequest)
        assertEquals(b, "400 Bad Request")
        assertEquals(count(registry, Timer("server.default.4xx-responses")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.get-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.4xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register a 5xx response") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](uri = uri"/internal-server-error")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.InternalServerError)
        assertEquals(b, "500 Internal Server Error")
        assertEquals(count(registry, Timer("server.default.5xx-responses")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.get-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.5xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register a GET request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](method = GET, uri = uri"/ok")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(b, "200 OK")
        assertEquals(count(registry, Timer("server.default.get-requests")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.get-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.2xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register a POST request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](method = POST, uri = uri"/ok")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(b, "200 OK")
        assertEquals(count(registry, Timer("server.default.post-requests")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.post-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.2xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register a PUT request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](method = PUT, uri = uri"/ok")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(b, "200 OK")
        assertEquals(count(registry, Timer("server.default.put-requests")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.put-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.2xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register a DELETE request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](method = DELETE, uri = uri"/ok")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(b, "200 OK")
        assertEquals(count(registry, Timer("server.default.delete-requests")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.delete-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.2xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("register an error") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](method = GET, uri = uri"/error")

    meteredRoutes.orNotFound(req).attempt.map { resp =>
      val Left(_) = resp
      assertEquals(count(registry, Timer("server.default.errors")), 1L)
      assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
      assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
      assertEquals(
        valuesOf(registry, Timer("server.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("server.default.get-requests"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
    }
  }

  test("register an abnormal termination") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test9")
    val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
    val req = Request[IO](method = GET, uri = uri"/abnormal-termination")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.body.attempt.compile.lastOrError.map { b =>
        assertEquals(resp.status, Status.Ok)
        val Left(_) = b
        assertEquals(count(registry, Timer("server.default.abnormal-terminations")), 1L)
        assertEquals(count(registry, Counter("server.default.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.default.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.default.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.default.get-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  test("use the provided request classifier") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val classifierFunc = (_: Request[IO]) => Some("classifier")
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test10")
    val meteredRoutes =
      Metrics[IO](ops = Dropwizard(registry, "server"), classifierF = classifierFunc)(testRoutes)
    val req = Request[IO](uri = uri"/ok")

    meteredRoutes.orNotFound(req).flatMap { resp =>
      resp.as[String].map { b =>
        assertEquals(resp.status, Status.Ok)
        assertEquals(b, "200 OK")
        assertEquals(count(registry, Timer("server.classifier.2xx-responses")), 1L)
        assertEquals(count(registry, Counter("server.classifier.active-requests")), 0L)
        assertEquals(count(registry, Timer("server.classifier.requests.total")), 1L)
        assertEquals(
          valuesOf(registry, Timer("server.classifier.requests.headers"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(50000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.classifier.get-requests"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
        assertEquals(
          valuesOf(registry, Timer("server.classifier.2xx-responses"))
            .getOrElse(Array.empty[Long])
            .toList,
          List(100000000L)
        )
      }
    }
  }

  private def testRoutes = HttpRoutes.of[IO](stub)
}
