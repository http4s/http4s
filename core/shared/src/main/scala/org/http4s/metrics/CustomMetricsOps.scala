/*
 * Copyright 2013 http4s.org
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

package org.http4s.metrics

import cats.~>
import org.http4s.RequestPrelude
import org.http4s.ResponsePrelude
import org.http4s.Status
import org.http4s.util.SizedSeq
import org.http4s.util.SizedSeq0

import scala.concurrent.duration.FiniteDuration

/** Describes an algebra capable of writing metrics to a metrics registry
  */
trait CustomMetricsOps[F[_], SL <: SizedSeq[String]] extends MetricsOps[F] {

  /** @return the custom label object used to define this CustomMetricsOps
    */
  def definingCustomLabels: CustomLabels[SL]

  /** Increases the count of active requests
    *
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def increaseActiveRequests(
      request: RequestPrelude,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def increaseActiveRequests(
      request: RequestPrelude,
      classifier: Option[String],
  ): F[Unit] =
    increaseActiveRequests(request, classifier, definingCustomLabels.values)

  /** Decreases the count of active requests
    *
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def decreaseActiveRequests(
      request: RequestPrelude,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def decreaseActiveRequests(
      request: RequestPrelude,
      classifier: Option[String],
  ): F[Unit] =
    decreaseActiveRequests(request, classifier, definingCustomLabels.values)

  /** Records the time to receive the response headers
    *
    * @param request the http request
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def recordHeadersTime(
      request: RequestPrelude,
      elapsed: FiniteDuration,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordHeadersTime(
      request: RequestPrelude,
      elapsed: FiniteDuration,
      classifier: Option[String],
  ): F[Unit] = recordHeadersTime(request, elapsed, classifier, definingCustomLabels.values)

  /** Records the time to fully consume the response, including the body
    *
    * @param request the http request
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def recordTotalTime(
      request: RequestPrelude,
      status: Option[Status],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordTotalTime(
      request: RequestPrelude,
      status: Option[Status],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      classifier: Option[String],
  ): F[Unit] = recordTotalTime(
    request,
    status,
    terminationType,
    elapsed,
    classifier,
    definingCustomLabels.values,
  )

  def recordRequestBodySize(
      request: RequestPrelude,
      status: Option[Status],
      terminationType: Option[TerminationType],
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordRequestBodySize(
      request: RequestPrelude,
      status: Option[Status],
      terminationType: Option[TerminationType],
      classifier: Option[String],
  ): F[Unit] =
    recordRequestBodySize(request, status, terminationType, classifier, definingCustomLabels.values)

  def recordResponseBodySize(
      request: RequestPrelude,
      response: ResponsePrelude,
      terminationType: Option[TerminationType],
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordResponseBodySize(
      request: RequestPrelude,
      response: ResponsePrelude,
      terminationType: Option[TerminationType],
      classifier: Option[String],
  ): F[Unit] =
    recordResponseBodySize(
      request,
      response,
      terminationType,
      classifier,
      definingCustomLabels.values,
    )

  /** Transform the effect of MetricOps using the supplied natural transformation
    *
    * @param fk natural transformation
    * @tparam G the effect to transform to
    * @return a new metric ops in the transformed effect
    */
  override def mapK[G[_]](fk: F ~> G): CustomMetricsOps[G, SL] = {
    val ops: CustomMetricsOps[F, SL] = this
    new CustomMetricsOps[G, SL] {
      override def definingCustomLabels: CustomLabels[SL] = ops.definingCustomLabels

      override def increaseActiveRequests(
          request: RequestPrelude,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(ops.increaseActiveRequests(request, classifier, customLabelValues))

      override def decreaseActiveRequests(
          request: RequestPrelude,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(ops.decreaseActiveRequests(request, classifier, customLabelValues))

      override def recordHeadersTime(
          request: RequestPrelude,
          elapsed: FiniteDuration,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(ops.recordHeadersTime(request, elapsed, classifier, customLabelValues))

      override def recordTotalTime(
          request: RequestPrelude,
          status: Option[Status],
          terminationType: Option[TerminationType],
          elapsed: FiniteDuration,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(
          ops.recordTotalTime(
            request,
            status,
            terminationType,
            elapsed,
            classifier,
            customLabelValues,
          )
        )

      override def recordRequestBodySize(
          request: RequestPrelude,
          status: Option[Status],
          terminationType: Option[TerminationType],
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(
          ops.recordRequestBodySize(request, status, terminationType, classifier, customLabelValues)
        )

      override def recordResponseBodySize(
          request: RequestPrelude,
          response: ResponsePrelude,
          terminationType: Option[TerminationType],
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(
          ops.recordResponseBodySize(
            request,
            response,
            terminationType,
            classifier,
            customLabelValues,
          )
        )

    }
  }
}

object CustomMetricsOps {
  def fromMetricsOps[F[_]](ops: MetricsOps[F]): CustomMetricsOps[F, SizedSeq0[String]] = {
    val emptyCustomLabels: EmptyCustomLabels = EmptyCustomLabels()

    new CustomMetricsOps[F, SizedSeq0[String]]() {

      override def definingCustomLabels: EmptyCustomLabels = emptyCustomLabels

      override def increaseActiveRequests(
          request: RequestPrelude,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] =
        ops.increaseActiveRequests(request, classifier)

      override def decreaseActiveRequests(
          request: RequestPrelude,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] =
        ops.decreaseActiveRequests(request, classifier)

      override def recordHeadersTime(
          request: RequestPrelude,
          elapsed: FiniteDuration,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] =
        ops.recordHeadersTime(request, elapsed, classifier)

      override def recordTotalTime(
          request: RequestPrelude,
          status: Option[Status],
          terminationType: Option[TerminationType],
          elapsed: FiniteDuration,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] =
        ops.recordTotalTime(request, status, terminationType, elapsed, classifier)

      override def recordRequestBodySize(
          request: RequestPrelude,
          status: Option[Status],
          terminationType: Option[TerminationType],
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] =
        ops.recordRequestBodySize(request, status, terminationType, classifier)

      override def recordResponseBodySize(
          request: RequestPrelude,
          response: ResponsePrelude,
          terminationType: Option[TerminationType],
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] =
        ops.recordResponseBodySize(request, response, terminationType, classifier)
    }
  }
}
