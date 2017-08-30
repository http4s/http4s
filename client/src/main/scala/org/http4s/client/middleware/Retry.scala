package org.http4s
package client
package middleware

import scala.concurrent.duration._
import scala.math.{pow, min, random}

import scalaz._
import scalaz.concurrent.Task
import org.http4s.Status._
import org.http4s.internal.compatibility._
import org.log4s.getLogger

object Retry {

  private[this] val logger = getLogger

  def apply(policy: RetryPolicy)(client: Client): Client = {
    def prepareLoop(req: Request, attempts: Int): Task[DisposableResponse] = {
      client.open(req).attempt flatMap {
        case \/-(dr) =>
          policy(req, \/-(dr.response), attempts) match {
            case Some(duration) =>
              logger.info(s"Request ${req} has failed on attempt #${attempts} with reason ${dr.response.status}. Retrying after ${duration}.")
              dr.dispose.flatMap(_ => nextAttempt(req, attempts, duration))
            case None =>
              Task.now(dr)
          }
        case left @ -\/(e) =>
          policy(req, left, attempts) match {
            case Some(duration) =>
              logger.error(e)(s"Request ${req} threw an exception on attempt #${attempts} attempts. Retrying after ${duration}.")
              nextAttempt(req, attempts, duration)
            case None =>
              Task.fail(e)
          }
      }
    }

    def nextAttempt(req: Request, attempts: Int, duration: FiniteDuration): Task[DisposableResponse] =
      // TODO honor Retry-After header
      Task.async { (prepareLoop(req.withEmptyBody, attempts + 1).get after duration).unsafePerformAsync }

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
  def apply(
    backoff: Int => Option[FiniteDuration],
    retriable: (Request, Throwable \/ Response) => Boolean = defaultRetriable
  ): RetryPolicy = { (req, result, retries) =>
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

  /** Default logic for whether a request is retriable. */
  def defaultRetriable(req: Request, result: Throwable \/ Response): Boolean = {
    if (req.isIdempotent)
      result match {
        case -\/(_) => true
        case \/-(resp) => RetriableStatuses(resp.status)
      }
    else
      false
  }

  def exponentialBackoff(maxWait: Duration, maxRetry: Int): Int => Option[FiniteDuration] = {
    val maxInMillis = maxWait.toMillis
    k => if (k > maxRetry) None else Some(expBackoff(k, maxInMillis))
  }

  private def expBackoff(k: Int, maxInMillis: Long): FiniteDuration = {
    val millis = (pow(2.0, k.toDouble) - 1.0) * 1000.0
    val interval = min(millis, maxInMillis.toDouble)
    FiniteDuration((random * interval).toLong, MILLISECONDS)
  }
}
