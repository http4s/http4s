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

package org.http4s.metrics.prometheus

import cats.syntax.either._
import cats.effect.SyncIO
import munit.CatsEffectSuite
import org.http4s.Http4sSuite

class PrometheusMetricsNamesSuite extends Http4sSuite {
  self: CatsEffectSuite =>
  private val parsedDefaultMetricsNames =
    SyncIO
      .fromEither(
        PrometheusMetricsNames(
          responseDuration = "response_duration_seconds",
          activeRequests = "active_request_count",
          requests = "request_count",
          abnormalTerminations = "abnormal_terminations"
        )
      )
      .map(mn => FunFixture[PrometheusMetricsNames](_ => mn, _ => ()))

  private val parsedMetricsNamesWithColonsAndDigits =
    SyncIO
      .fromEither(
        for {
          updatedResponseDuration <- PrometheusMetricsNames.DefaultMetricsNames
            .withResponseDuration("response_duration_seconds:42")

          updatedActiveRequests <- updatedResponseDuration.withActiveRequests(
            "active_request_count:42")

          updatedRequests <- updatedActiveRequests.withRequests("request_count:42")

          result <- updatedRequests.withAbnormalTerminations("abnormal_terminations:42")

        } yield result
      )
      .map(mn => FunFixture[PrometheusMetricsNames](_ => mn, _ => ()))

  private val parsingFailure =
    SyncIO
      .fromEither(
        PrometheusMetricsNames.DefaultMetricsNames
          .withResponseDuration("response_duration_seconds!")
      )
      .attempt
      .map {
        case Left(err) =>
          FunFixture[Either[Throwable, PrometheusMetricsNames]](_ => Either.left(err), _ => ())

        case Right(value) =>
          FunFixture[Either[Throwable, PrometheusMetricsNames]](_ => Either.right(value), _ => ())
      }

  private val anotherParsingFailure =
    SyncIO
      .fromEither(
        PrometheusMetricsNames(
          responseDuration = "response_duration_seconds",
          activeRequests = "active_request_count",
          requests = "request_count",
          abnormalTerminations = "abnormal_terminations?!"
        )
      )
      .attempt
      .map {
        case Left(err) =>
          FunFixture[Either[Throwable, PrometheusMetricsNames]](_ => Either.left(err), _ => ())

        case Right(value) =>
          FunFixture[Either[Throwable, PrometheusMetricsNames]](_ => Either.right(value), _ => ())
      }

  parsedDefaultMetricsNames.test(
    "The default Prometheus metrics' names should be parsed correctly") { metricNames =>
    assertEquals(metricNames, PrometheusMetricsNames.DefaultMetricsNames)
  }

  parsedMetricsNamesWithColonsAndDigits
    .test(
      "A Prometheus metrics' name should be updated with value included digits and colons correctly") {
      metricsNamesWithColonsAndDigits =>
        assertEquals(
          metricsNamesWithColonsAndDigits.responseDuration,
          "response_duration_seconds:42")
        assertEquals(metricsNamesWithColonsAndDigits.activeRequests, "active_request_count:42")
        assertEquals(metricsNamesWithColonsAndDigits.requests, "request_count:42")
        assertEquals(
          metricsNamesWithColonsAndDigits.abnormalTerminations,
          "abnormal_terminations:42")
    }

  parsingFailure
    .test(
      "A Prometheus metrics' name shouldn't be parsed with non letter, digit, underscore or colon ASCII symbols") {
      case Left(err) =>
        assertEquals(
          err.getMessage,
          s"""Metric name - "response_duration_seconds!" does not match regex - ([a-zA-Z_:][a-zA-Z0-9_:]*)"""
        )

      case Right(_) =>
        fail("PrometheusMetricsNames should fail to parse incorrect metric name")
    }

  anotherParsingFailure
    .test(
      "A Prometheus metrics' name shouldn't be parsed with non letter, digit, underscore or colon ASCII symbols") {
      case Left(err) =>
        assertEquals(
          err.getMessage,
          s"""Metric name - "abnormal_terminations?!" does not match regex - ([a-zA-Z_:][a-zA-Z0-9_:]*)"""
        )

      case Right(_) =>
        fail("PrometheusMetricsNames should fail to parse incorrect metric name")
    }
}
