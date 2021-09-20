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

package org.http4s
package client
package middleware

import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.all._
import org.http4s.Status._
import org.http4s.headers.{`Idempotency-Key`, `Retry-After`}
import org.log4s.getLogger
import org.typelevel.ci.CIString

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._
import scala.math.{min, pow, random}
import cats.effect.std.Hotswap

object Retry {
  private[this] val logger = getLogger

  def apply[F[_]](
      policy: RetryPolicy[F],
      redactHeaderWhen: CIString => Boolean = Headers.SensitiveHeaders.contains)(client: Client[F])(
      implicit F: Temporal[F]): Client[F] = {
    def showRequest(request: Request[F], redactWhen: CIString => Boolean): String = {
      val headers = request.headers.redactSensitive(redactWhen).headers.mkString(",")
      val uri = request.uri.renderString
      val method = request.method
      s"method=$method uri=$uri headers=$headers"
    }

    def nextAttempt(
        req: Request[F],
        attempts: Int,
        duration: FiniteDuration,
        retryHeader: Option[`Retry-After`],
        hotswap: Hotswap[F, Either[Throwable, Response[F]]]): F[Response[F]] = {
      val headerDuration =
        retryHeader
          .map { h =>
            h.retry match {
              case Left(d) => Instant.now().until(d.toInstant, ChronoUnit.SECONDS)
              case Right(secs) => secs
            }
          }
          .getOrElse(0L)
      val sleepDuration = headerDuration.seconds.max(duration)
      F.sleep(sleepDuration) >> retryLoop(req, attempts + 1, hotswap)
    }

    def retryLoop(
        req: Request[F],
        attempts: Int,
        hotswap: Hotswap[F, Either[Throwable, Response[F]]]): F[Response[F]] =
      hotswap.swap(client.run(req).attempt).flatMap {
        case Right(response) =>
          policy(req, Right(response), attempts) match {
            case Some(duration) =>
              logger.info(
                s"Request ${showRequest(req, redactHeaderWhen)} has failed on attempt #${attempts} with reason ${response.status}. Retrying after ${duration}.")
              nextAttempt(req, attempts, duration, response.headers.get[`Retry-After`], hotswap)
            case None =>
              F.pure(response)
          }

        case Left(e) =>
          policy(req, Left(e), attempts) match {
            case Some(duration) =>
              // info instead of error(e), because e is not discarded
              logger.info(e)(
                s"Request threw an exception on attempt #$attempts. Retrying after $duration")
              nextAttempt(req, attempts, duration, None, hotswap)
            case None =>
              logger.info(e)(
                s"Request ${showRequest(req, redactHeaderWhen)} threw an exception on attempt #$attempts. Giving up."
              )
              F.raiseError(e)
          }
      }

    Client { req =>
      Hotswap.create[F, Either[Throwable, Response[F]]].flatMap { hotswap =>
        Resource.eval(retryLoop(req, 1, hotswap))
      }
    }
  }
}

object RetryPolicy {

  /** Decomposes a retry policy into components that are typically configured
    * individually.
    *
    * @param backoff a function of attempts to an optional
    * FiniteDuration.  Return None to stop retrying, or some
    * duration after which the request will be retried.  See
    * `exponentialBackoff` for a useful implementation.
    *
    * @param retriable determines whether the request is retriable
    * from the request and either the throwable or response that was
    * returned.  Defaults to `defaultRetriable`.
    */
  def apply[F[_]](
      backoff: Int => Option[FiniteDuration],
      retriable: (Request[F], Either[Throwable, Response[F]]) => Boolean = defaultRetriable[F] _
  ): RetryPolicy[F] = { (req, result, retries) =>
    if (retriable(req, result)) backoff(retries)
    else None
  }

  /** Statuses that are retriable, per HTTP spec */
  val RetriableStatuses = Set(
    RequestTimeout,
    // TODO Leaving PayloadTooLarge out until we model Retry-After
    InternalServerError,
    ServiceUnavailable,
    BadGateway,
    GatewayTimeout
  )

  /** Returns true if (the request method is idempotent or request contains Idempotency-Key header)
    * and the result is either a throwable or has one of the `RetriableStatuses`.
    *
    * Caution: if the request body is effectful, the effects will be
    * run twice.  The most common symptom of this will be resubmitting
    * an idempotent request.
    */
  def defaultRetriable[F[_]](req: Request[F], result: Either[Throwable, Response[F]]): Boolean =
    (req.method.isIdempotent || req.headers.get[`Idempotency-Key`].isDefined) &&
      isErrorOrRetriableStatus(result)

  /** Like [[defaultRetriable]], but returns true even if the request method
    * is not idempotent.  This is useful if failed requests are assumed to
    * have not reached their destination, which is a dangerous assumption.
    * Use at your own risk.
    *
    * Caution: if the request body is effectful, the effects will be
    * run twice.  The most common symptom of this will be resubmitting
    * an empty request body.
    */
  def recklesslyRetriable[F[_]](result: Either[Throwable, Response[F]]): Boolean =
    isErrorOrRetriableStatus(result)

  /** Returns true if parameter is a Left or if the response contains a retriable status(as per HTTP spec) */
  def isErrorOrRetriableStatus[F[_]](result: Either[Throwable, Response[F]]): Boolean =
    isErrorOrStatus(result, RetriableStatuses)

  /** Like `isErrorOrRetriableStatus` but allows the caller to specify which statuses are considered retriable */
  def isErrorOrStatus[F[_]](result: Either[Throwable, Response[F]], status: Set[Status]): Boolean =
    result match {
      case Right(resp) => status(resp.status)
      case Left(WaitQueueTimeoutException) => false
      case _ => true
    }

  def exponentialBackoff(maxWait: Duration, maxRetry: Int): Int => Option[FiniteDuration] = {
    val maxInMillis = maxWait.toMillis
    k => if (k > maxRetry) None else Some(expBackoff(k, maxInMillis))
  }

  private def expBackoff(k: Int, maxInMillis: Long): FiniteDuration = {
    val millis = (pow(2.0, k.toDouble) - 1.0) * 1000.0
    val interval = min(millis, maxInMillis.toDouble)
    FiniteDuration((random() * interval).toLong, MILLISECONDS)
  }
}
