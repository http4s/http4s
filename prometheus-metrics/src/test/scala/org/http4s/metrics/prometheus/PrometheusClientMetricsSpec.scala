package org.http4s.metrics.prometheus

import cats.effect.{Clock, IO, Sync}
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.{TimeoutException, TimeUnit}
import org.http4s.{Http4sSpec, HttpApp, Request, Response, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.dsl.io._
import org.http4s.Method.GET
import org.http4s.client.middleware.Metrics
import scala.concurrent.duration.TimeUnit

class PrometheusMetricsSpec extends Http4sSpec {

  val client = Client.fromHttpApp[IO](RemoteEndpointStub.mockEndpoints)

  "A http client with a prometheus metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "2xx_headers_duration") must beEqualTo(0.05)
      count(registry, "2xx_total_duration") must beEqualTo(0.1)
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("badrequest").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, "4xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "4xx_headers_duration") must beEqualTo(0.05)
      count(registry, "4xx_total_duration") must beEqualTo(0.1)
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("error").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, "5xx_responses") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
      count(registry, "5xx_headers_duration") must beEqualTo(0.05)
      count(registry, "5xx_total_duration") must beEqualTo(0.1)
    }

    "register a GET request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "get") must beEqualTo(1)
      count(registry, "active_requests", "get") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "get") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "get") must beEqualTo(0.1)
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](POST, Uri.unsafeFromString("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "post") must beEqualTo(1)
      count(registry, "active_requests", "post") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "post") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "post") must beEqualTo(0.1)
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](PUT, Uri.unsafeFromString("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "put") must beEqualTo(1)
      count(registry, "active_requests", "put") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "put") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "put") must beEqualTo(0.1)
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](DELETE, Uri.unsafeFromString("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "delete") must beEqualTo(1)
      count(registry, "active_requests", "delete") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "delete") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "delete") must beEqualTo(0.1)
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("clienterror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, "errors") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "register a timeout" in {
      implicit val clock = FakeClock[IO]
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("timeout").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[TimeoutException]
      }
      count(registry, "timeouts") must beEqualTo(1)
      count(registry, "active_requests") must beEqualTo(0)
    }

    "use the provided request classifier" in {
      implicit val clock = FakeClock[IO]
      val classifier = (_: Request[IO]) => Some("classifier")
      val registry: CollectorRegistry = new CollectorRegistry()
      val meteredClient = Metrics(Prometheus(registry, "client"), classifier)(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, "2xx_responses", "get", "classifier") must beEqualTo(1)
      count(registry, "active_requests", "get", "classifier") must beEqualTo(0)
      count(registry, "2xx_headers_duration", "get", "classifier") must beEqualTo(0.05)
      count(registry, "2xx_total_duration", "get", "classifier") must beEqualTo(0.1)
    }
  }

  def count(registry: CollectorRegistry, name: String, method: String = "get", classifier: String = ""): Double =
    name match {
      case "active_requests" =>
        registry.getSampleValue(
          "client_active_request_count",
          Array("classifier"),
          Array(classifier))
      case "2xx_responses" =>
        registry
          .getSampleValue(
            "client_response_total",
            Array("classifier", "method", "status"),
            Array(classifier, method, "2xx"))
      case "2xx_headers_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "method", "response_phase"),
          Array(classifier, method, "response_received"))
      case "2xx_total_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "method", "response_phase"),
          Array(classifier, method, "body_processed"))
      case "4xx_responses" =>
        registry
          .getSampleValue(
            "client_response_total",
            Array("classifier", "method", "status"),
            Array(classifier, method, "4xx"))
      case "4xx_headers_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "method", "response_phase"),
          Array(classifier, method, "response_received"))
      case "4xx_total_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "method", "response_phase"),
          Array(classifier, method, "body_processed"))
      case "5xx_responses" =>
        registry
          .getSampleValue(
            "client_response_total",
            Array("classifier", "method", "status"),
            Array(classifier, method, "5xx"))
      case "5xx_headers_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "method", "response_phase"),
          Array(classifier, method, "response_received"))
      case "5xx_total_duration" =>
        registry.getSampleValue(
          "client_response_duration_seconds_sum",
          Array("classifier", "method", "response_phase"),
          Array(classifier, method, "body_processed"))
      case "errors" =>
        registry.getSampleValue(
          "client_abnormal_terminations_count",
          Array("classifier", "termination_type"),
          Array(classifier, "error"))
      case "timeouts" =>
        registry.getSampleValue(
          "client_abnormal_terminations_count",
          Array("classifier", "termination_type"),
          Array(classifier, "timeout"))
    }
}

object FakeClock {
  def apply[F[_]: Sync] = new Clock[F] {
    private var count = 0L

    override def realTime(unit: TimeUnit): F[Long] = {
      count += 50
      Sync[F].delay(unit.convert(count, TimeUnit.MILLISECONDS))
    }

    override def monotonic(unit: TimeUnit): F[Long] = {
      count += 50
      Sync[F].delay(unit.convert(count, TimeUnit.MILLISECONDS))
    }
  }
}

object RemoteEndpointStub {

  val mockEndpoints = HttpApp[IO] {
    case GET -> Root / "badrequest" =>
      BadRequest("400 Bad Request")
    case GET -> Root / "error" =>
      InternalServerError("500 Internal Server Error")
    case _ -> Root / "ok" =>
      Ok("200 OK")
    case GET -> Root / "clienterror" =>
      IO.raiseError[Response[IO]](new IOException("connection error"))
    case GET -> Root / "timeout" =>
      IO.raiseError[Response[IO]](new TimeoutException("request timed out"))
    case _ =>
      NotFound("404 Not Found")
  }
}
