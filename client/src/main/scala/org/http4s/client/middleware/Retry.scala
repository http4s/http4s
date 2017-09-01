package org.http4s
package client
package middleware

import java.time.Instant

import cats._
import cats.effect.Async
import cats.implicits._
import fs2._
import org.http4s.Status._
import org.http4s.headers.`Retry-After`
import org.log4s.getLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.math.{min, pow, random}

import java.time.temporal.ChronoUnit

object Retry {

  private[this] val logger = getLogger

  def apply[F[_]](policy: RetryPolicy[F])(client: Client[F])(
      implicit F: Async[F],
      scheduler: Scheduler,
      executionContext: ExecutionContext): Client[F] = {
    def prepareLoop(req: Request[F], attempts: Int): F[DisposableResponse[F]] =
      client.open(req).attempt.flatMap {
        // TODO fs2 port - Reimplement request isIdempotent in some form
        case right @ Right(dr @ DisposableResponse(response, _)) =>
          policy(req, Right(dr.response), attempts) match {
            case Some(duration) =>
              logger.info(
                s"Request ${req} has failed on attempt #${attempts} with reason ${response.status}. Retrying after ${duration}.")
              dr.dispose.flatMap(_ =>
                nextAttempt(req, attempts, duration, response.headers.get(`Retry-After`)))
            case None =>
              F.pure(dr)
          }
        case Left(e) =>
          policy(req, Left(e), attempts) match {
            case Some(duration) =>
              // info instead of error(e), because e is not discarded
              logger.info(
                s"Request $req threw an exception on attempt #$attempts attempts. Giving up.")
              nextAttempt(req, attempts, duration, None)
            case None =>
              F.raiseError[DisposableResponse[F]](e)
          }
      }

    def nextAttempt(
        req: Request[F],
        attempts: Int,
        duration: FiniteDuration,
        retryHeader: Option[`Retry-After`] = None)(
        implicit F: Async[F],
        executionContext: ExecutionContext): F[DisposableResponse[F]] = {
      val headerDuration = retryHeader
        .map { h =>
          h.retry match {
            case Left(d) => Instant.now().until(d.toInstant, ChronoUnit.SECONDS)
            case Right(secs) => secs
          }
        }
        .getOrElse(0L)
      val sleepDuration = Math.max(headerDuration, duration.length).seconds
      scheduler.sleep_[F](sleepDuration).run >> prepareLoop(req.withEmptyBody, attempts + 1)
    }

    client.copy(open = Service.lift(prepareLoop(_, 1)))
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

  /** Default logic for whether a request is retriable.  Returns true if
    * the request method does not permit a body and the result is
    * either a throwable or has one of the `RetriableStatuses`.
    *
    * Caution: more restrictive than 0.16.  That would inspect the
    * body for effects, and resumbmit only if the body was pure (i.e.,
    * only emits and halts).  The fs2 algebra does not let us inspect
    * the stream for effects, so we can't safely resubmit.  For the
    * old behavior, use [[unsafeRetriable]].  To ignore the response
    * codes, see [[recklesslyRetriable]].
    */
  def defaultRetriable[F[_]](req: Request[F], result: Either[Throwable, Response[F]]): Boolean =
    if (req.method.isInstanceOf[Method.NoBody]) isErrorOrRetriableStatus(result)
    else false

  /** Returns true if the request method is idempotent and the result is
    * either a throwable or has one of the `RetriableStatuses`.  This is
    * the `defaultRetriable` behavior from 0.16.
    *
    * Caution: if the request body is effectful, the effects will be
    * run twice.  The most common symptom of this will be resubmitting
    * an empty request body.
    */
  def unsafeRetriable[F[_]](req: Request[F], result: Either[Throwable, Response[F]]): Boolean =
    if (req.method.isIdempotent) isErrorOrRetriableStatus(result)
    else false

  /** Like [[unsafeRetriable]], but returns true even if the request method
    * is not idempotent.  This is useful if failed requests are assumed to
    * have not reached their destination, which is a dangerous assumption.
    * Use at your own risk.
    *
    * Caution: if the request body is effectful, the effects will be
    * run twice.  The most common symptom of this will be resubmitting
    * an empty request body.
    */
  def recklesslyRetriable[F[_]](req: Request[F], result: Either[Throwable, Response[F]]): Boolean =
    isErrorOrRetriableStatus(result)

  private def isErrorOrRetriableStatus[F[_]](result: Either[Throwable, Response[F]]): Boolean =
    result match {
      case Left(_) => true
      case Right(resp) => RetriableStatuses(resp.status)
    }

  def exponentialBackoff(maxWait: Duration, maxRetry: Int): Int => Option[FiniteDuration] = {
    val maxInMillis = maxWait.toMillis
    k =>
      if (k > maxRetry) None else Some(expBackoff(k, maxInMillis))
  }

  private def expBackoff(k: Int, maxInMillis: Long): FiniteDuration = {
    val millis = (pow(2.0, k.toDouble) - 1.0) * 1000.0
    val interval = min(millis, maxInMillis.toDouble)
    FiniteDuration((random * interval).toLong, MILLISECONDS)
  }
}
