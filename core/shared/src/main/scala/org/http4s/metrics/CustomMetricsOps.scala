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
import org.http4s.util.{SizedSeq, SizedSeq0}
import org.http4s.{Method, Status}

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
  def increaseActiveRequests(classifier: Option[String], customLabelValues: SL): F[Unit]
  override def increaseActiveRequests(classifier: Option[String]): F[Unit] =
    increaseActiveRequests(classifier, definingCustomLabels.values)

  /** Decreases the count of active requests
    *
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def decreaseActiveRequests(classifier: Option[String], customLabelValues: SL): F[Unit]
  override def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
    decreaseActiveRequests(classifier, definingCustomLabels.values)

  /** Records the time to receive the response headers
    *
    * @param method the http method of the request
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def recordHeadersTime(
      method: Method,
      elapsed: Long,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordHeadersTime(
      method: Method,
      elapsed: Long,
      classifier: Option[String],
  ): F[Unit] = recordHeadersTime(method, elapsed, classifier, definingCustomLabels.values)

  /** Records the time to fully consume the response, including the body
    *
    * @param method the http method of the request
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def recordTotalTime(
      method: Method,
      status: Status,
      elapsed: Long,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordTotalTime(
      method: Method,
      status: Status,
      elapsed: Long,
      classifier: Option[String],
  ): F[Unit] = recordTotalTime(method, status, elapsed, classifier, definingCustomLabels.values)

  /** Record abnormal terminations, like errors, timeouts or just other abnormal terminations.
    *
    * @param elapsed the time to record
    * @param terminationType the type of termination
    * @param classifier the classifier to apply
    * @param customLabelValues values for custom labels
    */
  def recordAbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
      classifier: Option[String],
      customLabelValues: SL,
  ): F[Unit]
  override def recordAbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
      classifier: Option[String],
  ): F[Unit] =
    recordAbnormalTermination(elapsed, terminationType, classifier, definingCustomLabels.values)

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
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(ops.increaseActiveRequests(classifier, customLabelValues))

      override def decreaseActiveRequests(
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(ops.decreaseActiveRequests(classifier, customLabelValues))

      override def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] = fk(ops.recordHeadersTime(method, elapsed, classifier, customLabelValues))

      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] = fk(ops.recordTotalTime(method, status, elapsed, classifier, customLabelValues))

      override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String],
          customLabelValues: SL,
      ): G[Unit] =
        fk(ops.recordAbnormalTermination(elapsed, terminationType, classifier, customLabelValues))

    }
  }
}

object CustomMetricsOps {
  def fromMetricsOps[F[_]](ops: MetricsOps[F]): CustomMetricsOps[F, SizedSeq0[String]] = {
    val emptyCustomLabels: EmptyCustomLabels = EmptyCustomLabels()

    new CustomMetricsOps[F, SizedSeq0[String]]() {

      override def definingCustomLabels: EmptyCustomLabels = emptyCustomLabels

      override def increaseActiveRequests(
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] = ops.increaseActiveRequests(classifier)

      override def decreaseActiveRequests(
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] = ops.decreaseActiveRequests(classifier)

      override def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] = ops.recordHeadersTime(method, elapsed, classifier)

      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] = ops.recordTotalTime(method, status, elapsed, classifier)

      override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String],
          customLabelValues: SizedSeq0[String],
      ): F[Unit] = ops.recordAbnormalTermination(elapsed, terminationType, classifier)
    }
  }
}
