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

  private[this] val RetriableStatuses = Set(
    RequestTimeout,
    // TODO Leaving PayloadTooLarge out until we model Retry-After
    InternalServerError,
    ServiceUnavailable,
    BadGateway,
    GatewayTimeout
  )

  def apply(backoff: Int => Option[FiniteDuration])(client: Client): Client = {
    def prepareLoop(req: Request, attempts: Int): Task[DisposableResponse] = {
      client.open(req).attempt flatMap {
        case \/-(dr @ DisposableResponse(Response(status, _, _, _, _), _)) if req.isIdempotent && RetriableStatuses(status) =>
          backoff(attempts) match {
            case Some(duration) =>
              logger.info(s"Request ${req} has failed on attempt #${attempts} with reason ${status}. Retrying after ${duration}.")
              dr.dispose.flatMap(_ => nextAttempt(req, attempts, duration))
            case None =>
              logger.info(s"Request ${req} has failed on attempt #${attempts} with reason ${status}. Giving up.")
              Task.now(dr)
          }
        case \/-(dr) =>
          Task.now(dr)
        case -\/(e) if req.isIdempotent =>
          backoff(attempts) match {
            case Some(duration) =>
              logger.error(e)(s"Request ${req} threw an exception on attempt #${attempts} attempts. Retrying after ${duration}.")
              nextAttempt(req, attempts, duration)
            case None =>
              // info instead of error(e), because e is not discarded
              logger.info(s"Request ${req} threw an exception on attempt #${attempts} attempts. Giving up.")
              Task.fail(e)
          }
        case -\/(e) =>
          Task.fail(e)
      }
    }

    def nextAttempt(req: Request, attempts: Int, duration: FiniteDuration): Task[DisposableResponse] =
      // TODO honor Retry-After header
      Task.async { (prepareLoop(req.withEmptyBody, attempts + 1).get after duration).unsafePerformAsync }

    client.copy(open = Service.lift(prepareLoop(_, 1)))
  }
}


object RetryPolicy {

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
