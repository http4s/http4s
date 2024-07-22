/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware

import cats.effect.IO
import cats.effect.Ref
import cats.effect.testkit.TestControl
import cats.syntax.applicative._
import com.comcast.ip4s._
import munit.ScalaCheckSuite
import org.http4s.Http4sSuite
import org.http4s.HttpRoutes
import org.http4s.HttpVersion
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.io._
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.NetworkProtocol
import org.http4s.metrics.TerminationType
import org.http4s.syntax.literals._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import scala.concurrent.duration._

class MetricsSuite extends Http4sSuite with ScalaCheckSuite {
  import MetricsSuite._

  private val DelayGen = Gen.posNum[Int].map(_.millis)

  private val PayloadGen = {
    val empty =
      for {
        delay <- DelayGen
      } yield Payload.Empty(delay)

    val stream =
      for {
        content <- Gen.alphaNumStr
        perChunkDelay <- DelayGen
      } yield Payload.Stream(content, perChunkDelay)

    val strict =
      for {
        content <- Gen.alphaNumStr
        delay <- DelayGen
      } yield Payload.Strict(content, delay)

    Gen.oneOf(empty, stream, strict)
  }

  test("get - 200 ok") {
    PropF.forAllF(PayloadGen) { server =>
      val routes = HttpRoutes.of[IO] { case GET -> Root / "ok" =>
        server match {
          case Payload.Empty(delay) => Ok().delayBy(delay)
          case s @ Payload.Stream(_, _) => Ok(s.stream)
          case Payload.Strict(body, delay) => Ok(body).delayBy(delay)
        }
      }

      val request =
        Request[IO](GET, uri"/ok").withAttribute(Request.Keys.ConnectionInfo, connection)

      val headersTime = server match {
        case Payload.Stream(_, _) => Duration.Zero
        case Payload.Empty(delay) => delay
        case Payload.Strict(_, delay) => delay
      }

      val totalTime = server match {
        case s @ Payload.Stream(_, _) => headersTime + s.totalDelay
        case Payload.Empty(_) => headersTime
        case Payload.Strict(_, _) => headersTime
      }

      val responseSize = getResponseSize(server)
      val status = Status.Ok
      val expected = Vector(
        MetricsOp.IncreaseActiveRequests(request.method, request.uri),
        MetricsOp.RecordHeadersTime(request.method, request.uri, headersTime),
        MetricsOp.DecreaseActiveRequests(request.method, request.uri),
        MetricsOp.RecordTotalTime(request.method, request.uri, Some(status), totalTime),
        MetricsOp.RecordRequestBodySize(request.method, request.uri, Some(status), None),
        MetricsOp.RecordResponseBodySize(request.method, request.uri, Some(status), responseSize),
      )

      TestControl.executeEmbed {
        for {
          metricsOps <- mkMetricsOps
          r <- Metrics(metricsOps)(routes).orNotFound(request)
          _ <- r.bodyText.compile.drain
          ops <- metricsOps.all
        } yield assertEquals(ops, expected)
      }
    }
  }

  test("post - 200 ok") {
    PropF.forAllF(PayloadGen, PayloadGen, Gen.oneOf(true, false)) { (client, server, consumeBody) =>
      val routes = HttpRoutes.of[IO] { case req @ POST -> Root / "ok" =>
        val response = server match {
          case Payload.Empty(delay) => Ok().delayBy(delay)
          case s @ Payload.Stream(_, _) => Ok(s.stream)
          case Payload.Strict(body, delay) => Ok(body).delayBy(delay)
        }

        req.bodyText.compile.foldMonoid.whenA(consumeBody) >> response
      }

      val request = mkPostRequest(client)

      val headersTime = {
        val ct = client match {
          case s @ Payload.Stream(_, _) if consumeBody => s.totalDelay
          case _ => Duration.Zero
        }

        val st = server match {
          case Payload.Stream(_, _) => Duration.Zero
          case Payload.Empty(delay) => delay
          case Payload.Strict(_, delay) => delay
        }

        ct + st
      }

      val totalTime = server match {
        case s @ Payload.Stream(_, _) => headersTime + s.totalDelay
        case Payload.Empty(_) => headersTime
        case Payload.Strict(_, _) => headersTime
      }

      val requestSize = getRequestSize(client)
      val responseSize = getResponseSize(server)
      val status = Status.Ok
      val expected = Vector(
        MetricsOp.IncreaseActiveRequests(request.method, request.uri),
        MetricsOp.RecordHeadersTime(request.method, request.uri, headersTime),
        MetricsOp.DecreaseActiveRequests(request.method, request.uri),
        MetricsOp.RecordTotalTime(request.method, request.uri, Some(status), totalTime),
        MetricsOp.RecordRequestBodySize(request.method, request.uri, Some(status), requestSize),
        MetricsOp.RecordResponseBodySize(request.method, request.uri, Some(status), responseSize),
      )

      TestControl.executeEmbed {
        for {
          metricsOps <- mkMetricsOps
          r <- Metrics(metricsOps)(routes).orNotFound(request)
          _ <- r.bodyText.compile.drain
          ops <- metricsOps.all
        } yield assertEquals(ops, expected)
      }
    }
  }

  test("post - 500 unhandled error") {
    PropF.forAllF(PayloadGen, DelayGen, Gen.oneOf(true, false)) { (client, serverDelay, consume) =>
      val error = new RuntimeException("something went wrong")

      val routes = HttpRoutes.of[IO] { case req @ POST -> Root / "ok" =>
        req.bodyText.compile.foldMonoid.whenA(consume) >>
          IO.raiseError(error).delayBy(serverDelay)
      }

      val request = mkPostRequest(client)

      val headersTime = client match {
        case s @ Payload.Stream(_, _) if consume => serverDelay + s.totalDelay
        case _ => serverDelay
      }

      val totalTime =
        headersTime

      val requestSize = getRequestSize(client)
      val status = Status.InternalServerError
      val tt = TerminationType.Error(error)
      val expected = Vector(
        MetricsOp.IncreaseActiveRequests(request.method, request.uri),
        MetricsOp.DecreaseActiveRequests(request.method, request.uri),
        MetricsOp.RecordHeadersTime(request.method, request.uri, headersTime),
        MetricsOp.RecordTotalTime(request.method, request.uri, Some(status), totalTime, Some(tt)),
        MetricsOp
          .RecordRequestBodySize(request.method, request.uri, Some(status), requestSize, Some(tt)),
        MetricsOp.RecordResponseBodySize(request.method, request.uri, Some(status), None, Some(tt)),
      )

      TestControl.executeEmbed {
        for {
          metricsOps <- mkMetricsOps
          _ <- Metrics(metricsOps)(routes).orNotFound(request).attempt
          ops <- metricsOps.all
        } yield assertEquals(ops, expected)
      }
    }
  }

  test("post - cancelation") {
    PropF.forAllF(PayloadGen, Gen.oneOf(true, false)) { (client, consume) =>
      val routes =
        HttpRoutes.of[IO] { case req @ POST -> Root / "ok" =>
          req.bodyText.compile.foldMonoid.whenA(consume) >> IO.canceled >> Ok()
        }

      val request = mkPostRequest(client)

      val headersTime = client match {
        case s @ Payload.Stream(_, _) if consume => s.totalDelay
        case _ => Duration.Zero
      }

      val totalTime =
        headersTime

      val requestSize = getRequestSize(client)
      val tt = TerminationType.Canceled
      val expected = Vector(
        MetricsOp.IncreaseActiveRequests(request.method, request.uri),
        MetricsOp.DecreaseActiveRequests(request.method, request.uri),
        MetricsOp.RecordTotalTime(request.method, request.uri, None, totalTime, Some(tt)),
        MetricsOp.RecordRequestBodySize(request.method, request.uri, None, requestSize, Some(tt)),
        MetricsOp.RecordResponseBodySize(request.method, request.uri, None, None, Some(tt)),
      )

      TestControl.executeEmbed {
        for {
          metricsOps <- mkMetricsOps
          f <- Metrics(metricsOps)(routes).orNotFound(request).void.start
          _ <- f.joinWithUnit
          ops <- metricsOps.all
        } yield assertEquals(ops, expected)
      }
    }
  }

  private def mkPostRequest(payload: Payload): Request[IO] = {
    val req = Request[IO](POST, uri"/ok").withAttribute(Request.Keys.ConnectionInfo, connection)

    payload match {
      case Payload.Empty(_) => req
      case s @ Payload.Stream(_, _) => req.withBodyStream(s.stream)
      case Payload.Strict(body, _) => req.withEntity(body)
    }
  }

  private def getRequestSize(payload: Payload): Option[Long] =
    payload match {
      case Payload.Strict(content, _) => Some(content.length.toLong)
      case Payload.Stream(_, _) => None
      case Payload.Empty(_) => None
    }

  private def getResponseSize(payload: Payload): Option[Long] =
    payload match {
      case Payload.Strict(content, _) => Some(content.length.toLong)
      case Payload.Stream(_, _) => None
      case Payload.Empty(_) => Some(0L)
    }

  private def mkMetricsOps: IO[InMemoryMetricsOps] =
    for {
      ops <- IO.ref(Vector.empty[MetricsOp])
    } yield new InMemoryMetricsOps(ops)

  private class InMemoryMetricsOps(ops: Ref[IO, Vector[MetricsOp]]) extends MetricsOps[IO] {
    def all: IO[Vector[MetricsOp]] = ops.get

    private def add(op: MetricsOp): IO[Unit] =
      ops.update(_ :+ op)

    def increaseActiveRequests(
        method: Method,
        uri: Uri,
        address: Option[SocketAddress[IpAddress]],
        classifier: Option[String],
    ): IO[Unit] =
      add(MetricsOp.IncreaseActiveRequests(method, uri, address, classifier))

    def decreaseActiveRequests(
        method: Method,
        uri: Uri,
        address: Option[SocketAddress[IpAddress]],
        classifier: Option[String],
    ): IO[Unit] =
      add(MetricsOp.DecreaseActiveRequests(method, uri, address, classifier))

    def recordHeadersTime(
        method: Method,
        uri: Uri,
        protocol: NetworkProtocol,
        address: Option[SocketAddress[IpAddress]],
        elapsed: FiniteDuration,
        classifier: Option[String],
    ): IO[Unit] =
      add(MetricsOp.RecordHeadersTime(method, uri, elapsed, protocol, address, classifier))

    def recordTotalTime(
        method: Method,
        uri: Uri,
        protocol: NetworkProtocol,
        address: Option[SocketAddress[IpAddress]],
        status: Option[Status],
        terminationType: Option[TerminationType],
        elapsed: FiniteDuration,
        classifier: Option[String],
    ): IO[Unit] =
      add(
        MetricsOp.RecordTotalTime(
          method,
          uri,
          status,
          elapsed,
          terminationType,
          protocol,
          address,
          classifier,
        )
      )

    def recordRequestBodySize(
        method: Method,
        uri: Uri,
        protocol: NetworkProtocol,
        address: Option[SocketAddress[IpAddress]],
        status: Option[Status],
        terminationType: Option[TerminationType],
        contentLength: Option[Long],
        classifier: Option[String],
    ): IO[Unit] =
      add(
        MetricsOp.RecordRequestBodySize(
          method,
          uri,
          status,
          contentLength,
          terminationType,
          protocol,
          address,
          classifier,
        )
      )

    def recordResponseBodySize(
        method: Method,
        uri: Uri,
        protocol: NetworkProtocol,
        address: Option[SocketAddress[IpAddress]],
        status: Option[Status],
        terminationType: Option[TerminationType],
        contentLength: Option[Long],
        classifier: Option[String],
    ): IO[Unit] =
      add(
        MetricsOp.RecordResponseBodySize(
          method,
          uri,
          status,
          contentLength,
          terminationType,
          protocol,
          address,
          classifier,
        )
      )
  }

}

object MetricsSuite {

  private val protocol = NetworkProtocol.http(HttpVersion.`HTTP/1.1`)
  private val connection = Request.Connection(
    local = SocketAddress(ip"127.0.0.1", port"8081"),
    remote = SocketAddress(ip"127.0.0.1", port"8082"),
    secure = true,
  )

  private sealed trait MetricsOp
  private object MetricsOp {
    final case class IncreaseActiveRequests(
        method: Method,
        uri: Uri,
        address: Option[SocketAddress[IpAddress]] = Some(connection.local),
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class DecreaseActiveRequests(
        method: Method,
        uri: Uri,
        address: Option[SocketAddress[IpAddress]] = Some(connection.local),
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordHeadersTime(
        method: Method,
        uri: Uri,
        elapsed: FiniteDuration,
        protocol: NetworkProtocol = protocol,
        address: Option[SocketAddress[IpAddress]] = Some(connection.local),
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordTotalTime(
        method: Method,
        uri: Uri,
        status: Option[Status],
        elapsed: FiniteDuration,
        terminationType: Option[TerminationType] = None,
        protocol: NetworkProtocol = protocol,
        address: Option[SocketAddress[IpAddress]] = Some(connection.local),
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordRequestBodySize(
        method: Method,
        uri: Uri,
        status: Option[Status],
        contentLength: Option[Long],
        terminationType: Option[TerminationType] = None,
        protocol: NetworkProtocol = protocol,
        address: Option[SocketAddress[IpAddress]] = Some(connection.local),
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordResponseBodySize(
        method: Method,
        uri: Uri,
        status: Option[Status],
        contentLength: Option[Long],
        terminationType: Option[TerminationType] = None,
        protocol: NetworkProtocol = protocol,
        address: Option[SocketAddress[IpAddress]] = Some(connection.local),
        classifier: Option[String] = None,
    ) extends MetricsOp
  }

  private sealed trait Payload extends Product with Serializable
  private object Payload {
    final case class Empty(responseDelay: FiniteDuration) extends Payload

    final case class Stream(
        content: String,
        perChunkDelay: FiniteDuration,
    ) extends Payload {
      def stream: fs2.Stream[IO, Byte] =
        fs2.Stream
          .emits(content.getBytes)
          .covary[IO]
          .zipLeft(fs2.Stream.fixedDelay[IO](perChunkDelay))

      def totalDelay: FiniteDuration = content.length * perChunkDelay
    }

    final case class Strict(
        content: String,
        responseDelay: FiniteDuration,
    ) extends Payload
  }

}
