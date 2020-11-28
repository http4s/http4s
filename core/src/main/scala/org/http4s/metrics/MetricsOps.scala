/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.metrics

import cats.Foldable
import org.http4s.{Method, Request, Status, Uri}

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
      classifier: Option[String]): F[Unit]

  /** Record abnormal terminations, like errors, timeouts or just other abnormal terminations.
    *
    * @param elapsed the time to record
    * @param terminationType the type of termination
    * @param classifier the classifier to apply
    */
  def recordAbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
      classifier: Option[String]): F[Unit]
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
      pathSeparator: String = "_"
  ): Request[F] => Option[String] = { request: Request[F] =>
    val initial: String = request.method.name

    val pathList: List[String] =
      requestToPathList(request)

    val minusExcluded: List[String] = pathList.map { (value: String) =>
      if (exclude(value)) excludedValue else value
    }

    val result: String =
      minusExcluded match {
        case Nil => initial
        case nonEmpty @ _ :: _ =>
          initial + pathSeparator + Foldable[List]
            .intercalate(nonEmpty, pathSeparator)
      }

    Some(result)
  }

  // The following was copied from
  // https://github.com/http4s/http4s/blob/v0.20.17/dsl/src/main/scala/org/http4s/dsl/impl/Path.scala#L56-L64,
  // and then modified.
  private def requestToPathList[F[_]](request: Request[F]): List[String] = {
    val str: String = request.pathInfo

    if (str == "" || str == "/")
      Nil
    else {
      val segments = str.split("/", -1)
      // .head is safe because split always returns non-empty array
      val segments0 = if (segments.head == "") segments.drop(1) else segments
      val reversed: List[String] =
        segments0.foldLeft[List[String]](Nil)((path, seg) => Uri.decode(seg) :: path)
      reversed.reverse
    }
  }

}

/** Describes the type of abnormal termination */
sealed trait TerminationType

object TerminationType {

  /** Signals just a generic abnormal termination */
  case object Abnormal extends TerminationType

  /** Signals an abnormal termination due to an error processing the request, either at the server or client side */
  case object Error extends TerminationType

  /** Signals a client timing out during a request */
  case object Timeout extends TerminationType
}
