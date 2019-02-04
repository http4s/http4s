package org.http4s.metrics.prometheus

import cats.effect.IO
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.http4s.{Http4sSpec, HttpApp, Request, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.middleware.Metrics
import org.http4s.dsl.io._
import org.http4s.metrics.prometheus.util._

class PrometheusClientMetricsSpec extends Http4sSpec {

  val client = Client.fromHttpApp[IO](HttpApp[IO](stub))

  "A http client with a prometheus metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp = meteredClient.flatMap(_.expect[String]("ok")).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "client") must beEqualTo(1)
      count(registry, "active_requests", "client") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "client") must beEqualTo(0.1)
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val prom = Prometheus[IO](registry, "client")
      val meteredClient = prom.map(Metrics(_)(client))

      val resp =
        meteredClient.flatMap(_.expect[String]("bad-request")).attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, "4xx_responses", "client") must beEqualTo(1)
      count(registry, "active_requests", "client") must beEqualTo(0)
      count(registry, "4xx_headers_duration", "client") must beEqualTo(0.05)
      count(registry, "4xx_total_duration", "client") must beEqualTo(0.1)
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp =
        meteredClient.flatMap(_.expect[String]("internal-server-error")).attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, "5xx_responses", "client") must beEqualTo(1)
      count(registry, "active_requests", "client") must beEqualTo(0)
      count(registry, "5xx_headers_duration", "client") must beEqualTo(0.05)
      count(registry, "5xx_total_duration", "client") must beEqualTo(0.1)
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp = meteredClient.flatMap(_.expect[String]("ok")).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "client", "get") must beEqualTo(1)
      count(registry, "active_requests", "client", "get") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client", "get") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "client", "get") must beEqualTo(0.1)
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp = meteredClient
        .flatMap(
          _.expect[String](Request[IO](POST, Uri.unsafeFromString("ok")))
        )
        .attempt
        .unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "client", "post") must beEqualTo(1)
      count(registry, "active_requests", "client", "post") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client", "post") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "client", "post") must beEqualTo(0.1)
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp = meteredClient
        .flatMap(_.expect[String](Request[IO](PUT, Uri.unsafeFromString("ok"))))
        .attempt
        .unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "client", "put") must beEqualTo(1)
      count(registry, "active_requests", "client", "put") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client", "put") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "client", "put") must beEqualTo(0.1)
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp = meteredClient
        .flatMap(_.expect[String](Request[IO](DELETE, Uri.unsafeFromString("ok"))))
        .attempt
        .unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "client", "delete") must beEqualTo(1)
      count(registry, "active_requests", "client", "delete") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client", "delete") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "client", "delete") must beEqualTo(0.1)
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp =
        meteredClient.flatMap(_.expect[String]("error")).attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, "errors", "client") must beEqualTo(1)
      count(registry, "active_requests", "client") must beEqualTo(0)
    }

    "register a timeout" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_)(client))

      val resp =
        meteredClient.flatMap(_.expect[String]("timeout")).attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[TimeoutException]
      }
      count(registry, "timeouts", "client") must beEqualTo(1)
      count(registry, "active_requests", "client") must beEqualTo(0)
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val classifier = (_: Request[IO]) => Some("classifier")
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Prometheus(registry, "client").map(Metrics(_, classifier)(client))

      val resp = meteredClient.flatMap(_.expect[String]("ok")).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "client", "get", "classifier") must beEqualTo(1)
      count(registry, "active_requests", "client", "get", "classifier") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client", "get", "classifier") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "client", "get", "classifier") must beEqualTo(0.1)
    }
  }

}
