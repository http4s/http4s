package org.http4s.metrics.dropwizard

import cats.effect.{Clock, IO, Sync}
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import java.io.IOException
import java.util.concurrent.{TimeoutException, TimeUnit}
import org.http4s.{Http4sSpec, HttpApp, Request, Response, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.dsl.io._
import org.http4s.Method.GET
import org.http4s.client.middleware.Metrics
import scala.concurrent.duration.TimeUnit

class DropwizardMetricsSpec extends Http4sSpec {

  val client = Client.fromHttpApp[IO](RemoteEndpointStub.mockEndpoints)

  "A http client with a dropwizard metrics middleware" should {

    "register a 2xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test1")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String]("ok").attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.2xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
    }

    "register a 4xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test2")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("badrequest").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(400)) => ok }
      }
      count(registry, Timer("client.default.4xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
    }

    "register a 5xx response" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test3")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("servererror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beLike { case UnexpectedStatus(Status(500)) => ok }
      }
      count(registry, Timer("client.default.5xx-responses")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.requests.total")) must beSome(Array(100000000L))
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
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.get-requests")) must beSome(Array(100000000L))
      values(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a POST request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test5")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](POST, Uri.unsafeFromString("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.post-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.post-requests")) must beSome(Array(100000000L))
      values(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a PUT request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test6")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](PUT, Uri.unsafeFromString("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.put-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.put-requests")) must beSome(Array(100000000L))
      values(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register a DELETE request" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test7")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp = meteredClient.expect[String](Request[IO](DELETE, Uri.unsafeFromString("ok"))).attempt.unsafeRunSync()

      resp must beRight { contain("200 OK") }
      count(registry, Timer("client.default.delete-requests")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      count(registry, Timer("client.default.requests.total")) must beEqualTo(1)
      values(registry, Timer("client.default.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.default.delete-requests")) must beSome(Array(100000000L))
      values(registry, Timer("client.default.2xx-responses")) must beSome(Array(100000000L))
    }

    "register an error" in {
      implicit val clock = FakeClock[IO]
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate("test8")
      val meteredClient = Metrics(Dropwizard(registry, "client"))(client)

      val resp =
        meteredClient.expect[String]("clienterror").attempt.unsafeRunSync()

      resp must beLeft { (e: Throwable) =>
        e must beAnInstanceOf[IOException]
      }
      count(registry, Timer("client.default.errors")) must beEqualTo(1)
      count(registry, Counter("client.default.active-requests")) must beEqualTo(0)
      values(registry, Timer("client.default.requests.headers")) must beNone
      values(registry, Timer("client.default.requests.total")) must beNone
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
      values(registry, Timer("client.default.requests.headers")) must beEqualTo(None)
      values(registry, Timer("client.default.requests.total")) must beEqualTo(None)
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
      values(registry, Timer("client.get.requests.headers")) must beSome(Array(50000000L))
      values(registry, Timer("client.get.requests.total")) must beSome(Array(100000000L))
    }
  }

  def count(registry: MetricRegistry, counter: Counter): Long =
    registry.getCounters.get(counter.value).getCount

  def count(registry: MetricRegistry, timer: Timer): Long =
    registry.getTimers.get(timer.value).getCount

  def values(registry: MetricRegistry, timer: Timer): Option[Array[Long]] =
    Option(registry.getTimers().get(timer.value)).map(_.getSnapshot.getValues)

  case class Counter(value: String)
  case class Timer(value: String)
}

object FakeClock {
  def apply[F[_] : Sync] = new Clock[F] {
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
    case GET -> Root / "servererror" =>
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
