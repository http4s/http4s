package org.http4s
package client
package middleware

import cats.effect.{Resource, Sync, Timer}
import cats.implicits._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.http4s.Status._
import org.http4s.headers.`Retry-After`
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger
import scala.concurrent.duration._
import scala.math.{min, pow, random}

object Retry {

  private[this] val logger = getLogger

  def apply[F[_]](
      policy: RetryPolicy[F],
      redactHeaderWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      client: Client[F])(implicit F: Sync[F], T: Timer[F]): Client[F] = {
    def prepareLoop(req: Request[F], attempts: Int): Resource[F, Response[F]] =
      client.run(req).attempt.flatMap {
        case right @ Right(response) =>
          policy(req, right, attempts) match {
            case Some(duration) =>
              logger.info(
                s"Request ${showRequest(req, redactHeaderWhen)} has failed on attempt #${attempts} with reason ${response.status}. Retrying after ${duration}.")
              nextAttempt(req, attempts, duration, response.headers.get(`Retry-After`))
            case None =>
              Resource.pure(response)
          }

        case left @ Left(e) =>
          policy(req, left, attempts) match {
            case Some(duration) =>
              // info instead of error(e), because e is not discarded
              logger.info(e)(
                s"Request threw an exception on attempt #$attempts. Retrying after $duration")
              nextAttempt(req, attempts, duration, None)
            case None =>
              logger.info(e)(
                s"Request ${showRequest(req, redactHeaderWhen)} threw an exception on attempt #$attempts. Giving up."
              )
              Resource.liftF(F.raiseError(e))
          }
      }

    def showRequest(request: Request[F], redactWhen: CaseInsensitiveString => Boolean): String = {
      val headers = request.headers.redactSensitive(redactWhen).toList.mkString(",")
      val uri = request.uri.renderString
      val method = request.method
      s"method=$method uri=$uri headers=$headers"
    }

    def nextAttempt(
        req: Request[F],
        attempts: Int,
        duration: FiniteDuration,
        retryHeader: Option[`Retry-After`]): Resource[F, Response[F]] = {
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
      Resource.liftF(T.sleep(sleepDuration)) *> prepareLoop(req, attempts + 1)
    }

    Client(prepareLoop(_, 1))
  }

  @deprecated("The `redactHeaderWhen` parameter is now available on `apply`.", "0.19.1")
  def retryWithRedactedHeaders[F[_]](
      policy: RetryPolicy[F],
      redactHeaderWhen: CaseInsensitiveString => Boolean)(
      client: Client[F])(implicit F: Sync[F], T: Timer[F]): Client[F] =
    apply(policy, redactHeaderWhen)(client)
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

  /** Returns true if the request method is idempotent and the result is
    * either a throwable or has one of the `RetriableStatuses`.
    *
    * Caution: if the request body is effectful, the effects will be
    * run twice.  The most common symptom of this will be resubmitting
    * an idempotent request.
    */
  def defaultRetriable[F[_]](req: Request[F], result: Either[Throwable, Response[F]]): Boolean =
    req.method.isIdempotent && isErrorOrRetriableStatus(result)

  @deprecated("Use defaultRetriable instead", "0.19.0")
  def unsafeRetriable[F[_]](req: Request[F], result: Either[Throwable, Response[F]]): Boolean =
    defaultRetriable(req, result)

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

  private def isErrorOrRetriableStatus[F[_]](result: Either[Throwable, Response[F]]): Boolean =
    result match {
      case Right(resp) => RetriableStatuses(resp.status)
      case Left(WaitQueueTimeoutException) => false
      case _ => true
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
