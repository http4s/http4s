package org.http4s.metrics.dropwizard

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import java.io.IOException
import java.util.concurrent.TimeoutException

import org.http4s.{EntityDecoder, Http4sSpec, HttpApp, Request, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.middleware.Metrics
import org.http4s.dsl.io._
import org.http4s.metrics.dropwizard.util._
import org.http4s.Uri.uri

import scala.util.Try

class DropwizardMetricsSpec extends Http4sSpec {

  val client = Client.fromHttpApp[IO](HttpApp[IO](stub))

  "A http client with a dropwizard metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("bad-request").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, Timer("client.default.4xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("internal-server-error").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, Timer("client.default.5xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test4")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.get-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.get-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](POST, uri("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.post-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.post-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](PUT, uri("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.put-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.put-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String](Request[IO](DELETE, uri("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.delete-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.delete-requests")) must beSome(Array(100000000L))
      valuesOf(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("error").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, Timer("client.default.errors")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.default.requests.headers")) must beNone
      valuesOf(registry, Timer("client.default.requests.total")) must beNone
    }

    "register a timeout" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test9")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("timeout").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[TimeoutException]
      }
      count(registry, Timer("client.default.timeouts")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.default.requests.headers")) must beEqualTo(None)
      valuesOf(registry, Timer("client.default.requests.total")) must beEqualTo(None)
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test10")
      val meteredClient = Metrics(Dropwizard(registry, "client"), requestMethodClassifier)(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.get.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.get.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.get.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.get.requests.total")) must beSome(Array(100000000L))
    }

    "only record total time and decr active requests after client.run releases" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test11")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val clientRunResource = meteredClient
        .run(Request[IO](uri = Uri.unsafeFromString("ok")))
        .use { resp =>
          IO {
            (EntityDecoder[IO, String].decode(resp, false).value.unsafeRunSync() must beRight {
              contain("200 OK")
            }).and(count(registry, Counter("client.default.active-requests")) must beEqualTo(1))
              .and(valuesOf(registry, Timer("client.default.requests.headers")) must beSome(
                Array(50000000L)))
              .and(Try(count(registry, Timer("client.default.2xx-responses"))).toOption must beNone)
              .and(valuesOf(registry, Timer("client.default.requests.total")) must beNone)
          }
        }
        .unsafeRunSync()

      clientRunResource.isSuccess
      count(registry, Timer("client.default.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      valuesOf(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      valuesOf(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
    }
  }
}
