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

import cats.data.NonEmptyList

final case class PrometheusMetricsSettings(
    metricsNames: PrometheusMetricsNames,
    responseDurationSecondsHistogramBuckets: NonEmptyList[Double]
) {
  def withMetricsNames(metricsNames: PrometheusMetricsNames): PrometheusMetricsSettings =
    copy(metricsNames = metricsNames)

  def withResponseDurationSecondsHistogramBuckets(
      responseDurationSecondsHistogramBuckets: NonEmptyList[Double]
  ): PrometheusMetricsSettings =
    copy(responseDurationSecondsHistogramBuckets = responseDurationSecondsHistogramBuckets)
}

object PrometheusMetricsSettings {
  // https://github.com/prometheus/client_java/blob/parent-0.6.0/simpleclient/src/main/java/io/prometheus/client/Histogram.java#L73
  private val DefaultHistogramBuckets: NonEmptyList[Double] =
    NonEmptyList(.005, List(.01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10))

  val DefaultSettings: PrometheusMetricsSettings =
    PrometheusMetricsSettings(
      metricsNames = PrometheusMetricsNames.DefaultMetricsNames,
      responseDurationSecondsHistogramBuckets = DefaultHistogramBuckets
    )
}
