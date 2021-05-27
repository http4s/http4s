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

import cats.syntax.apply._
import cats.syntax.either._
import org.http4s.metrics.prometheus.PrometheusMetricsNames._

/**
  * Metric name may contain ASCII letters and digits, as well as underscores and colons.
  * It must match the regex ([a-zA-Z_:][a-zA-Z0-9_:]*).
  * Notice that only a single metric with a given name can be registered within a [[io.prometheus.client.CollectorRegistry]].
  * Vice versa registering a metric with the same name more than once will yield an error.
  *
  * More details: https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels
  */
final class PrometheusMetricsNames private(
    val responseDuration: String,
    val activeRequests: String,
    val requests: String,
    val abnormalTerminations: String
) {
  override def equals(obj: Any): Boolean =
    obj match {
      case metricsNames: PrometheusMetricsNames =>
        responseDuration == metricsNames.responseDuration &&
          activeRequests == metricsNames.activeRequests &&
          requests == metricsNames.requests &&
          abnormalTerminations == metricsNames.abnormalTerminations

      case _ => false
    }

  private def copy(
      responseDuration: String = this.responseDuration,
      activeRequests: String = this.activeRequests,
      requests: String = this.requests,
      abnormalTerminations: String = this.abnormalTerminations
  ): PrometheusMetricsNames =
    new PrometheusMetricsNames(
      responseDuration = responseDuration,
      activeRequests = activeRequests,
      requests = requests,
      abnormalTerminations = abnormalTerminations
    )

  def withResponseDuration(responseDuration: String): Validated[PrometheusMetricsNames] =
    checkMetricName(responseDuration)
      .map(validatedName => copy(responseDuration = validatedName))

  def withActiveRequests(activeRequests: String): Validated[PrometheusMetricsNames] =
    checkMetricName(activeRequests)
      .map(validatedName => copy(activeRequests = validatedName))

  def withRequests(requests: String): Validated[PrometheusMetricsNames] =
    checkMetricName(requests)
      .map(validatedName => copy(requests = validatedName))

  def withAbnormalTerminations(abnormalTerminations: String): Validated[PrometheusMetricsNames] =
    checkMetricName(abnormalTerminations)
      .map(validatedName => copy(abnormalTerminations = validatedName))

  def withPrefix(prefix: String): Validated[PrometheusMetricsNames] =
    apply(
      responseDuration = prefix + "_" + responseDuration,
      activeRequests = prefix + "_" + activeRequests,
      requests = prefix + "_" + requests,
      abnormalTerminations = prefix + "_" + abnormalTerminations
    )
}

object PrometheusMetricsNames {
  type Validated[A] = Either[Throwable, A]

  private val nameReg = "([a-zA-Z_:][a-zA-Z0-9_:]*)".r

  private def checkMetricName(metricName: String): Validated[String] =
    metricName match {
      case nameReg(name) => Either.right(name)
      case _ =>
        Either.left(
          new IllegalArgumentException(
            s"""Metric name - "$metricName" does not match regex - ${nameReg.toString}"""
          )
        )
    }

  def apply(
      responseDuration: String,
      activeRequests: String,
      requests: String,
      abnormalTerminations: String
  ): Validated[PrometheusMetricsNames] = {
    (checkMetricName(responseDuration),
     checkMetricName(activeRequests),
     checkMetricName(requests),
     checkMetricName(abnormalTerminations))
      .mapN(new PrometheusMetricsNames(_, _, _, _))
  }

  /**
    * Notice that metric name must match the regex ([a-zA-Z_:][a-zA-Z0-9_:]*).
    * Otherwise, it will yield an error in registering metric within a [[io.prometheus.client.CollectorRegistry]].
    */
  def unsafeCreate(responseDuration: String,
                   activeRequests: String,
                   requests: String,
                   abnormalTerminations: String): PrometheusMetricsNames =
    new PrometheusMetricsNames(responseDuration = responseDuration,
                         activeRequests = activeRequests,
                         requests = requests,
                         abnormalTerminations = abnormalTerminations)

  val DefaultMetricsNames: PrometheusMetricsNames =
    new PrometheusMetricsNames(
      responseDuration = "response_duration_seconds",
      activeRequests = "active_request_count",
      requests = "request_count",
      abnormalTerminations = "abnormal_terminations"
    )
}
