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
import munit.ScalaCheckSuite
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Length`
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.syntax.literals._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import scala.concurrent.duration._

class MetricsSuite extends Http4sSuite with ScalaCheckSuite {
  import MetricsSuite._
  import MetricsOp._

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

      val request = Request[IO](GET, uri"/ok")

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
        IncreaseActiveRequests(request.method, request.uri),
        RecordHeadersTime(request.method, request.uri, headersTime),
        DecreaseActiveRequests(request.method, request.uri),
        RecordTotalTime(request.method, request.uri, Some(status), totalTime),
        RecordRequestBodySize(request.method, request.uri, Some(status), None),
        RecordResponseBodySize(request.method, request.uri, status, responseSize),
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
        IncreaseActiveRequests(request.method, request.uri),
        RecordHeadersTime(request.method, request.uri, headersTime),
        DecreaseActiveRequests(request.method, request.uri),
        RecordTotalTime(request.method, request.uri, Some(status), totalTime),
        RecordRequestBodySize(request.method, request.uri, Some(status), requestSize),
        RecordResponseBodySize(request.method, request.uri, status, responseSize),
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
        IncreaseActiveRequests(request.method, request.uri),
        DecreaseActiveRequests(request.method, request.uri),
        RecordHeadersTime(request.method, request.uri, headersTime),
        RecordTotalTime(request.method, request.uri, Some(status), totalTime, Some(tt)),
        RecordRequestBodySize(request.method, request.uri, Some(status), requestSize, Some(tt)),
        RecordResponseBodySize(request.method, request.uri, status, None, Some(tt)),
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
    val req = Request[IO](POST, uri"/ok")

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

    def increaseActiveRequests(request: RequestPrelude, classifier: Option[String]): IO[Unit] =
      add(IncreaseActiveRequests(request.method, request.uri, classifier))

    def decreaseActiveRequests(request: RequestPrelude, classifier: Option[String]): IO[Unit] =
      add(DecreaseActiveRequests(request.method, request.uri, classifier))

    def recordHeadersTime(
        request: RequestPrelude,
        elapsed: FiniteDuration,
        classifier: Option[String],
    ): IO[Unit] =
      add(RecordHeadersTime(request.method, request.uri, elapsed, classifier))

    def recordTotalTime(
        request: RequestPrelude,
        status: Option[Status],
        terminationType: Option[TerminationType],
        elapsed: FiniteDuration,
        classifier: Option[String],
    ): IO[Unit] =
      add(
        RecordTotalTime(request.method, request.uri, status, elapsed, terminationType, classifier)
      )

    def recordRequestBodySize(
        request: RequestPrelude,
        status: Option[Status],
        terminationType: Option[TerminationType],
        classifier: Option[String],
    ): IO[Unit] =
      add(
        RecordRequestBodySize(
          request.method,
          request.uri,
          status,
          request.headers.get[`Content-Length`].map(_.length),
          terminationType,
          classifier,
        )
      )

    def recordResponseBodySize(
        request: RequestPrelude,
        response: ResponsePrelude,
        terminationType: Option[TerminationType],
        classifier: Option[String],
    ): IO[Unit] =
      add(
        RecordResponseBodySize(
          request.method,
          request.uri,
          response.status,
          response.headers.get[`Content-Length`].map(_.length),
          terminationType,
          classifier,
        )
      )
  }

}

object MetricsSuite {

  private sealed trait MetricsOp
  private object MetricsOp {
    final case class IncreaseActiveRequests(
        method: Method,
        uri: Uri,
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class DecreaseActiveRequests(
        method: Method,
        uri: Uri,
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordHeadersTime(
        method: Method,
        uri: Uri,
        elapsed: FiniteDuration,
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordTotalTime(
        method: Method,
        uri: Uri,
        status: Option[Status],
        elapsed: FiniteDuration,
        terminationType: Option[TerminationType] = None,
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordRequestBodySize(
        method: Method,
        uri: Uri,
        status: Option[Status],
        contentLength: Option[Long],
        terminationType: Option[TerminationType] = None,
        classifier: Option[String] = None,
    ) extends MetricsOp

    final case class RecordResponseBodySize(
        method: Method,
        uri: Uri,
        status: Status,
        contentLength: Option[Long],
        terminationType: Option[TerminationType] = None,
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
