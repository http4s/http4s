package org.http4s.metrics.prometheus

import cats.effect.{Clock, IO, Resource}
import cats.syntax.functor._
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.http4s.{Http4sSpec, HttpApp, Request, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.middleware.Metrics
import org.http4s.dsl.io._
import org.http4s.metrics.prometheus.util._
import org.specs2.execute.AsResult

class PrometheusClientMetricsSpec extends Http4sSpec {

  val client: Client[IO] = Client.fromHttpApp[IO](HttpApp[IO](stub))

  "A http client with a prometheus metrics middleware" should {

    "register a 2xx response" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String]("ok").attempt
        } yield {
          resp must beRight { contain("200 OK") }

          count(registry, "2xx_responses", "client") must beEqualTo(1)
          count(registry, "active_requests", "client") must beEqualTo(0)
          count(registry, "2xx_headers_duration", "client") must beEqualTo(0.05)
          count(registry, "2xx_total_duration", "client") must beEqualTo(0.1)
        }
    }

    "register a 4xx response" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String]("bad-request").attempt
        } yield {
          resp must beLeft { e: Throwable =>
            e must beLike { case UnexpectedStatus(Status.BadRequest) => ok }
          }

          count(registry, "4xx_responses", "client") must beEqualTo(1)
          count(registry, "active_requests", "client") must beEqualTo(0)
          count(registry, "4xx_headers_duration", "client") must beEqualTo(0.05)
          count(registry, "4xx_total_duration", "client") must beEqualTo(0.1)
        }
    }

    "register a 5xx response" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String]("internal-server-error").attempt
        } yield {
          resp must beLeft { e: Throwable =>
            e must beLike { case UnexpectedStatus(Status.InternalServerError) => ok }
          }

          count(registry, "5xx_responses", "client") must beEqualTo(1)
          count(registry, "active_requests", "client") must beEqualTo(0)
          count(registry, "5xx_headers_duration", "client") must beEqualTo(0.05)
          count(registry, "5xx_total_duration", "client") must beEqualTo(0.1)
        }
    }

    "register a GET request" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String]("ok").attempt
        } yield {
          resp must beRight { contain("200 OK") }

          count(registry, "2xx_responses", "client", "get") must beEqualTo(1)
          count(registry, "active_requests", "client", "get") must beEqualTo(0)
          count(registry, "2xx_headers_duration", "client", "get") must beEqualTo(0.05)
          count(registry, "2xx_total_duration", "client", "get") must beEqualTo(0.1)
        }
    }

    "register a POST request" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String](Request[IO](POST, Uri.unsafeFromString("ok"))).attempt
        } yield {
          resp must beRight { contain("200 OK") }

          count(registry, "2xx_responses", "client", "post") must beEqualTo(1)
          count(registry, "active_requests", "client", "post") must beEqualTo(0)
          count(registry, "2xx_headers_duration", "client", "post") must beEqualTo(0.05)
          count(registry, "2xx_total_duration", "client", "post") must beEqualTo(0.1)
        }
    }

    "register a PUT request" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String](Request[IO](PUT, Uri.unsafeFromString("ok"))).attempt
        } yield {
          resp must beRight { contain("200 OK") }

          count(registry, "2xx_responses", "client", "put") must beEqualTo(1)
          count(registry, "active_requests", "client", "put") must beEqualTo(0)
          count(registry, "2xx_headers_duration", "client", "put") must beEqualTo(0.05)
          count(registry, "2xx_total_duration", "client", "put") must beEqualTo(0.1)
        }
    }

    "register a DELETE request" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String](Request[IO](DELETE, Uri.unsafeFromString("ok"))).attempt
        } yield {
          resp must beRight { contain("200 OK") }

          count(registry, "2xx_responses", "client", "delete") must beEqualTo(1)
          count(registry, "active_requests", "client", "delete") must beEqualTo(0)
          count(registry, "2xx_headers_duration", "client", "delete") must beEqualTo(0.05)
          count(registry, "2xx_total_duration", "client", "delete") must beEqualTo(0.1)
        }
    }

    "register an error" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String]("error").attempt
        } yield {
          resp must beLeft { e: Throwable =>
            e must beAnInstanceOf[IOException]
          }

          count(registry, "errors", "client") must beEqualTo(1)
          count(registry, "active_requests", "client") must beEqualTo(0)
        }
    }

    "register a timeout" in withMeteredClient {
      case (registry, client) =>
        for {
          resp <- client.expect[String]("timeout").attempt
        } yield {
          resp must beLeft { e: Throwable =>
            e must beAnInstanceOf[TimeoutException]
          }

          count(registry, "timeouts", "client") must beEqualTo(1)
          count(registry, "active_requests", "client") must beEqualTo(0)
        }
    }

    "use the provided request classifier" in {
      val classifier = (_: Request[IO]) => Some("classifier")

      meteredClient(classifier)
        .use {
          case (registry, client) =>
            for {
              resp <- client.expect[String]("ok").attempt
            } yield {
              resp must beRight { contain("200 OK") }

              count(registry, "2xx_responses", "client", "get", "classifier") must beEqualTo(1)
              count(registry, "active_requests", "client", "get", "classifier") must beEqualTo(0)
              count(registry, "2xx_headers_duration", "client", "get", "classifier") must beEqualTo(
                0.05)
              count(registry, "2xx_total_duration", "client", "get", "classifier") must beEqualTo(
                0.1)
            }
        }
        .unsafeRunSync()
    }

    "unregister collectors" in {
      val registry = meteredClient()
        .use { case (cr, client) => client.expect[String]("ok").as(cr) }
        .unsafeRunSync()

      count(registry, "2xx_responses", "client") must beEqualTo(0)
      count(registry, "active_requests", "client") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "client") must beEqualTo(0)
      count(registry, "2xx_total_duration", "client") must beEqualTo(0)
    }

  }

  private def withMeteredClient[R: AsResult](in: (CollectorRegistry, Client[IO]) => IO[R]): R =
    meteredClient().use(in.tupled).unsafeRunSync()

  private def meteredClient(
      classifier: Request[IO] => Option[String] = (_: Request[IO]) => None
  ): Resource[IO, (CollectorRegistry, Client[IO])] = {
    implicit val clock: Clock[IO] = FakeClock[IO]

    for {
      registry <- Prometheus.collectorRegistry[IO]
      metrics <- Prometheus.metricsOps[IO](registry, "client")
    } yield (registry, Metrics(metrics, classifier)(client))
  }

}
