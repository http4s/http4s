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

import cats.Foldable
import cats.~>
import org.http4s.Request
import org.http4s.RequestPrelude
import org.http4s.ResponsePrelude
import org.http4s.Status

import scala.concurrent.duration.FiniteDuration

/** Describes an algebra capable of writing metrics to a metrics registry.
  *
  * The algebra provides enough information to fill out all required and
  * optional [[https://opentelemetry.io/docs/specs/semconv/http/http-metrics OpenTelemetry attributes]].
  */
trait MetricsOps[F[_]] {

  /** Increases the count of active requests
    *
    * @param request the request
    * @param classifier the classifier to apply
    */
  def increaseActiveRequests(request: RequestPrelude, classifier: Option[String]): F[Unit]

  /** Decreases the count of active requests
    *
    * @param request the request
    * @param classifier the classifier to apply
    */
  def decreaseActiveRequests(request: RequestPrelude, classifier: Option[String]): F[Unit]

  /** Records the time to receive the response headers
    *
    * @param request the request
    * @param elapsed the headers receiving time
    * @param classifier the classifier to apply
    */
  def recordHeadersTime(
      request: RequestPrelude,
      elapsed: FiniteDuration,
      classifier: Option[String],
  ): F[Unit]

  /** Records the time to fully consume the response, including the body
    *
    * @param request the request
    * @param status the status of the response
    * @param terminationType the termination type
    * @param elapsed the processing time
    * @param classifier the classifier to apply
    */
  def recordTotalTime(
      request: RequestPrelude,
      status: Option[Status],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      classifier: Option[String],
  ): F[Unit]

  /** Records the size of the request body
    *
    * @param request the request
    * @param status the status of the response
    * @param terminationType the termination type
    * @param classifier the classifier to apply
    */
  def recordRequestBodySize(
      request: RequestPrelude,
      status: Option[Status],
      terminationType: Option[TerminationType],
      classifier: Option[String],
  ): F[Unit]

  /** Records the size of the response body
    *
    * @param request the request
    * @param response the response
    * @param terminationType the termination type
    * @param classifier the classifier to apply
    */
  def recordResponseBodySize(
      request: RequestPrelude,
      response: ResponsePrelude,
      terminationType: Option[TerminationType],
      classifier: Option[String],
  ): F[Unit]

  /** Transform the effect of MetricOps using the supplied natural transformation
    *
    * @param fk natural transformation
    * @tparam G the effect to transform to
    * @return a new metric ops in the transformed effect
    */
  def mapK[G[_]](fk: F ~> G): MetricsOps[G] = {
    val ops = this
    new MetricsOps[G] {
      override def increaseActiveRequests(
          request: RequestPrelude,
          classifier: Option[String],
      ): G[Unit] = fk(ops.increaseActiveRequests(request, classifier))
      override def decreaseActiveRequests(
          request: RequestPrelude,
          classifier: Option[String],
      ): G[Unit] = fk(ops.decreaseActiveRequests(request, classifier))
      override def recordHeadersTime(
          request: RequestPrelude,
          elapsed: FiniteDuration,
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordHeadersTime(request, elapsed, classifier))
      override def recordTotalTime(
          request: RequestPrelude,
          status: Option[Status],
          terminationType: Option[TerminationType],
          elapsed: FiniteDuration,
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordTotalTime(request, status, terminationType, elapsed, classifier))
      override def recordRequestBodySize(
          request: RequestPrelude,
          status: Option[Status],
          terminationType: Option[TerminationType],
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordRequestBodySize(request, status, terminationType, classifier))
      override def recordResponseBodySize(
          request: RequestPrelude,
          response: ResponsePrelude,
          terminationType: Option[TerminationType],
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordResponseBodySize(request, response, terminationType, classifier))
    }
  }
}

object MetricsOps {

  /** Given an exclude function, return a 'classifier' function, i.e. for application in
    * org.http4s.server/client.middleware.Metrics#apply.
    *
    * Let's say you want a classifier that excludes integers since your paths consist of:
    *   * GET    /users/{integer} = GET_users_*
    *   * POST   /users           = POST_users
    *   * PUT    /users/{integer} = PUT_users_*
    *   * DELETE /users/{integer} = DELETE_users_*
    *
    * In such a case, we could use:
    *
    * classifierFMethodWithOptionallyExcludedPath(
    *   exclude          = { str: String => scala.util.Try(str.toInt).isSuccess },
    *   excludedValue    = "*",
    *   intercalateValue = "_"
    * )
    *
    * Chris Davenport notes the following on performance considerations of exclude's function value:
    *
    * > It's worth noting that this runs on every segment of a path. So note that if an intermediate Throwables with
    * > Stack traces is known and discarded, there may be a performance penalty, such as the above example with Try(str.toInt).
    * > I benchmarked some approaches and regex matches should generally be preferred over Throwable's
    * > in this position.
    *
    * @param exclude For a given String, namely a path value, determine whether the value gets excluded.
    * @param excludedValue Indicates the String value to be supplied for an excluded path's field.
    * @param pathSeparator Value to use for separating the metrics fields' values
    * @return Request[F] => Option[String]
    */
  def classifierFMethodWithOptionallyExcludedPath[F[_]](
      exclude: String => Boolean,
      excludedValue: String = "*",
      pathSeparator: String = "_",
  ): Request[F] => Option[String] = { (request: Request[F]) =>
    val initial: String = request.method.name

    val excluded =
      request.pathInfo.segments
        .map { segment =>
          val decoded = segment.decoded()
          if (exclude(decoded)) excludedValue else decoded
        }

    val result =
      if (excluded.isEmpty)
        initial
      else
        initial + pathSeparator + Foldable[Vector]
          .intercalate(excluded, pathSeparator)

    Some(result)
  }
}

/** Describes the type of abnormal termination */
sealed trait TerminationType

object TerminationType {

  /** Signals just a generic abnormal termination */
  final case class Abnormal(rootCause: Throwable) extends TerminationType

  /** Signals cancelation */
  case object Canceled extends TerminationType

  /** Signals an abnormal termination due to an error processing the request, either at the server or client side */
  final case class Error(rootCause: Throwable) extends TerminationType

  /** Signals a client timing out during a request */
  case object Timeout extends TerminationType
}
