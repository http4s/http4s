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
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status

/** Describes an algebra capable of writing metrics to a metrics registry
  */
trait MetricsOps[F[_]] {

  /** Increases the count of active requests
    *
    * @param classifier the classifier to apply
    */
  def increaseActiveRequests(classifier: Option[String]): F[Unit]

  /** Decreases the count of active requests
    *
    * @param classifier the classifier to apply
    */
  def decreaseActiveRequests(classifier: Option[String]): F[Unit]

  /** Records the time to receive the response headers
    *
    * @param method the http method of the request
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    */
  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): F[Unit]

  /** Records the time to fully consume the response, including the body
    *
    * @param method the http method of the request
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    */
  def recordTotalTime(
      method: Method,
      status: Status,
      elapsed: Long,
      classifier: Option[String],
  ): F[Unit]

  /** Record abnormal terminations, like errors, timeouts or just other abnormal terminations.
    *
    * @param elapsed the time to record
    * @param terminationType the type of termination
    * @param classifier the classifier to apply
    */
  def recordAbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
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
      override def increaseActiveRequests(classifier: Option[String]): G[Unit] = fk(
        ops.increaseActiveRequests(classifier)
      )
      override def decreaseActiveRequests(classifier: Option[String]): G[Unit] = fk(
        ops.decreaseActiveRequests(classifier)
      )
      override def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordHeadersTime(method, elapsed, classifier))
      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordTotalTime(method, status, elapsed, classifier))
      override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordAbnormalTermination(elapsed, terminationType, classifier))
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
  // scalafix:off Http4sGeneralLinters; bincompat until 1.0

  /** Signals just a generic abnormal termination */
  case class Abnormal(rootCause: Throwable) extends TerminationType

  /** Signals cancelation */
  case object Canceled extends TerminationType

  /** Signals an abnormal termination due to an error processing the request, either at the server or client side */
  case class Error(rootCause: Throwable) extends TerminationType

  // scalafix:on

  /** Signals a client timing out during a request */
  case object Timeout extends TerminationType
}
