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
import cats.syntax.all._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SharedMetricRegistries
import org.http4s.EntityDecoder
import org.http4s.Http4sSuite
import org.http4s.HttpApp
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.client.UnexpectedStatus
import org.http4s.client.middleware.Metrics
import org.http4s.dsl.io._
import org.http4s.metrics.dropwizard.util._
import org.http4s.syntax.all._

import java.io.IOException
import java.util.Arrays
import java.util.concurrent.TimeoutException

class DropwizardClientMetricsSuite extends Http4sSuite {
  private val client = Client.fromHttpApp[IO](HttpApp[IO](stub))

  test("A http client with a dropwizard metrics middleware should register a 2xx response") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String]("/ok").attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))
      assertEquals(count(registry, Timer("client.default.2xx-responses")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.total"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register a 4xx response") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String]("/bad-request").attempt.map { resp =>
      val Left(UnexpectedStatus(status, _, _)) = resp
      assertEquals(status, BadRequest)
      assertEquals(count(registry, Timer("client.default.4xx-responses")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.total"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register a 5xx response") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String]("/internal-server-error").attempt.map { resp =>
      val Left(UnexpectedStatus(status, _, _)) = resp

      assertEquals(status, InternalServerError)
      assertEquals(count(registry, Timer("client.default.5xx-responses")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.total"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register a GET request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String]("/ok").attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))
      assertEquals(count(registry, Timer("client.default.get-requests")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(count(registry, Timer("client.default.requests.total")), 1L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.get-requests"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.2xx-responses"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register a POST request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String](Request[IO](POST, uri"/ok")).attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))
      assertEquals(count(registry, Timer("client.default.post-requests")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(count(registry, Timer("client.default.requests.total")), 1L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.post-requests"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.2xx-responses"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register a PUT request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String](Request[IO](PUT, uri"/ok")).attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))
      assertEquals(count(registry, Timer("client.default.put-requests")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(count(registry, Timer("client.default.requests.total")), 1L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.put-requests"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.2xx-responses"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register a DELETE request") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String](Request[IO](DELETE, uri"/ok")).attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))
      assertEquals(count(registry, Timer("client.default.delete-requests")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(count(registry, Timer("client.default.requests.total")), 1L)
      assertEquals(
        valuesOf(registry, Timer("client.default.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.delete-requests"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.default.2xx-responses"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  test("A http client with a dropwizard metrics middleware should register an error") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String]("/error").attempt.map { resp =>
      val Left(_: IOException) = resp
      assertEquals(count(registry, Timer("client.default.errors")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(valuesOf(registry, Timer("client.default.requests.headers")), None)
      assertEquals(valuesOf(registry, Timer("client.default.requests.total")), None)
    }
  }

  test("A http client with a dropwizard metrics middleware should register a timeout") {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test9")
    val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

    meteredClient.expect[String]("/timeout").attempt.map { resp =>
      val Left(_: TimeoutException) = resp
      assertEquals(count(registry, Timer("client.default.timeouts")), 1L)
      assertEquals(count(registry, Counter("client.default.active-requests")), 0L)
      assertEquals(valuesOf(registry, Timer("client.default.requests.headers")), None)
      assertEquals(valuesOf(registry, Timer("client.default.requests.total")), None)
    }
  }

  test(
    "A http client with a dropwizard metrics middleware should use the provided request classifier"
  ) {
    implicit val clock: Clock[IO] = FakeClock[IO]
    val requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
    val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test10")
    val meteredClient =
      Metrics(Dropwizard[IO](registry, "client"), requestMethodClassifier)(client)

    meteredClient.expect[String]("/ok").attempt.map { resp =>
      assertEquals(resp, Right("200 OK"))
      assertEquals(count(registry, Timer("client.get.2xx-responses")), 1L)
      assertEquals(count(registry, Counter("client.get.active-requests")), 0L)
      assertEquals(
        valuesOf(registry, Timer("client.get.requests.headers"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(50000000L)
      )
      assertEquals(
        valuesOf(registry, Timer("client.get.requests.total"))
          .getOrElse(Array.empty[Long])
          .toList,
        List(100000000L)
      )
    }
  }

  implicit val clock: Clock[IO] = FakeClock[IO]
  val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test11")
  val meteredClient = Metrics(Dropwizard[IO](registry, "client"))(client)

  val clientRunResource = meteredClient
    .run(Request[IO](uri = uri"/ok"))

  ResourceFixture(clientRunResource).test(
    "A http client with a dropwizard metrics middleware should only record total time and decr active requests after client.run releases"
  ) { resp =>
    IO {
      EntityDecoder[IO, String].decode(resp, false).value.map { r =>
        assertEquals(r, Right("200 OK"))
        assertEquals(count(registry, Counter("client.default.active-requests")), 1L)
        assertEquals(
          valuesOf(registry, Timer("client.default.requests.headers"))
            .map(Arrays.equals(_, Array(50000000L))),
          Some(true),
        )
        assertEquals(
          Either.catchNonFatal(count(registry, Timer("client.default.2xx-responses"))).toOption,
          None,
        )
        assertEquals(valuesOf(registry, Timer("client.default.requests.total")), none)
      }
    }
  }
}
