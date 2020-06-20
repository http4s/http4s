/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.metrics.prometheus

import cats.effect.{Clock, IO, Sync}
import fs2.Stream
import io.prometheus.client.CollectorRegistry
import java.io.IOException
import java.util.concurrent.{TimeUnit, TimeoutException}
import org.http4s.{Request, Response}
import org.http4s.dsl.io._
import org.http4s.Method.GET
import scala.concurrent.duration.TimeUnit

object util {
  def stub: PartialFunction[Request[IO], IO[Response[IO]]] = {
    case (GET | POST | PUT | DELETE) -> Root / "ok" =>
      Ok("200 OK")
    case GET -> Root / "bad-request" =>
      BadRequest("400 Bad Request")
    case GET -> Root / "internal-server-error" =>
      InternalServerError("500 Internal Server Error")
    case GET -> Root / "error" =>
      IO.raiseError[Response[IO]](new IOException("error"))
    case GET -> Root / "timeout" =>
      IO.raiseError[Response[IO]](new TimeoutException("request timed out"))
    case GET -> Root / "abnormal-termination" =>
      Ok("200 OK").map(
        _.withBodyStream(Stream.raiseError[IO](new RuntimeException("Abnormal termination"))))
    case GET -> Root / "never" =>
      IO.never
    case _ =>
      NotFound("404 Not Found")
  }

  def count(
      registry: CollectorRegistry,
      name: String,
      prefix: String,
      method: String = "get",
      classifier: String = "",
      cause: String = ""): Double =
    name match {
      case "active_requests" =>
        registry.getSampleValue(
          s"${prefix}_active_request_count",
          Array("classifier"),
          Array(classifier))
      case "2xx_responses" =>
        registry
          .getSampleValue(
            s"${prefix}_request_count",
            Array("classifier", "method", "status"),
            Array(classifier, method, "2xx"))
      case "2xx_headers_duration" =>
        registry.getSampleValue(
          s"${prefix}_response_duration_seconds_sum",
          Array("classifier", "method", "phase"),
          Array(classifier, method, "headers"))
      case "2xx_total_duration" =>
        registry.getSampleValue(
          s"${prefix}_response_duration_seconds_sum",
          Array("classifier", "method", "phase"),
          Array(classifier, method, "body"))
      case "4xx_responses" =>
        registry
          .getSampleValue(
            s"${prefix}_request_count",
            Array("classifier", "method", "status"),
            Array(classifier, method, "4xx"))
      case "4xx_headers_duration" =>
        registry.getSampleValue(
          s"${prefix}_response_duration_seconds_sum",
          Array("classifier", "method", "phase"),
          Array(classifier, method, "headers"))
      case "4xx_total_duration" =>
        registry.getSampleValue(
          s"${prefix}_response_duration_seconds_sum",
          Array("classifier", "method", "phase"),
          Array(classifier, method, "body"))
      case "5xx_responses" =>
        registry
          .getSampleValue(
            s"${prefix}_request_count",
            Array("classifier", "method", "status"),
            Array(classifier, method, "5xx"))
      case "5xx_headers_duration" =>
        registry.getSampleValue(
          s"${prefix}_response_duration_seconds_sum",
          Array("classifier", "method", "phase"),
          Array(classifier, method, "headers"))
      case "5xx_total_duration" =>
        registry.getSampleValue(
          s"${prefix}_response_duration_seconds_sum",
          Array("classifier", "method", "phase"),
          Array(classifier, method, "body"))
      case "errors" =>
        registry.getSampleValue(
          s"${prefix}_abnormal_terminations_count",
          Array("classifier", "termination_type", "cause"),
          Array(classifier, "error", cause))
      case "timeouts" =>
        registry.getSampleValue(
          s"${prefix}_abnormal_terminations_count",
          Array("classifier", "termination_type", "cause"),
          Array(classifier, "timeout", cause))
      case "abnormal_terminations" =>
        registry.getSampleValue(
          s"${prefix}_abnormal_terminations_count",
          Array("classifier", "termination_type", "cause"),
          Array(classifier, "abnormal", cause))
      case "cancels" =>
        registry.getSampleValue(
          s"${prefix}_abnormal_terminations_count",
          Array("classifier", "termination_type", "cause"),
          Array(classifier, "cancel", cause))
    }

  object FakeClock {
    def apply[F[_]: Sync] =
      new Clock[F] {
        private var count = 0L

        override def realTime(unit: TimeUnit): F[Long] =
          Sync[F].delay {
            count += 50
            unit.convert(count, TimeUnit.MILLISECONDS)
          }

        override def monotonic(unit: TimeUnit): F[Long] =
          Sync[F].delay {
            count += 50
            unit.convert(count, TimeUnit.MILLISECONDS)
          }
      }
  }
}
