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

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.metrics.dropwizard.util._
import org.http4s.server.middleware.Metrics
import org.http4s.testing.Http4sLegacyMatchersIO
import org.http4s.{Http4sSpec, HttpRoutes, Request, Status}

class DropwizardServerMetricsSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  "Http routes with a dropwizard metrics middleware" should {
    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.2xx-responses")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)

      val req = Request[IO](uri = uri("/bad-request"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.BadRequest)
      resp must haveBody("400 Bad Request")
      count(registry, Timer("server.default.4xx-responses")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.4xx-responses")) must beSome(Array(100000000L))
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](uri = uri("/internal-server-error"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.InternalServerError)
      resp must haveBody("500 Internal Server Error")
      count(registry, Timer("server.default.5xx-responses")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.5xx-responses")) must beSome(Array(100000000L))
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.get-requests")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](method = POST, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.post-requests")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.post-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](method = PUT, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.put-requests")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.put-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](method = DELETE, uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.default.delete-requests")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.delete-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/error"))

      val resp = meteredRoutes.orNotFound(req).attempt.unsafeRunSync()

      resp must beLeft
      count(registry, Timer("server.default.errors")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
    }

    "register an abnormal termination" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test9")
      val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
      val req = Request[IO](method = GET, uri = uri("/abnormal-termination"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp.body.attempt.compile.lastOrError.unsafeRunSync() must beLeft
      count(registry, Timer("server.default.abnormal-terminations")) must beEqualTo(1L)
      count(registry, Counter("server.default.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.default.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.default.get-requests")) must beSome(Array(100000000L))
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val classifierFunc = (_: Request[IO]) => Some("classifier")
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test10")
      val meteredRoutes =
        Metrics[IO](ops = Dropwizard(registry, "server"), classifierF = classifierFunc)(testRoutes)
      val req = Request[IO](uri = uri("/ok"))

      val resp = meteredRoutes.orNotFound(req).unsafeRunSync()

      resp must haveStatus(Status.Ok)
      resp must haveBody("200 OK")
      count(registry, Timer("server.classifier.2xx-responses")) must beEqualTo(1L)
      count(registry, Counter("server.classifier.active-requests")) must beEqualTo(0L)
      count(registry, Timer("server.classifier.requests.total")) must beEqualTo(1L)
      valuesOf(registry, Timer("server.classifier.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("server.classifier.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("server.classifier.2xx-responses")) must beSome(Array(100000000L))
    }
  }

  private def testRoutes = HttpRoutes.of[IO](stub)
}
