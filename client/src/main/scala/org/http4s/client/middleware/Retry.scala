package org.http4s
package client
package middleware

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats._
import cats.implicits._
import fs2._
import org.http4s.Status._
import org.http4s.headers.`Retry-After`
import org.log4s.getLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.math.{min, pow, random}

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

  def apply(backoff: Int => Option[FiniteDuration])(client: Client)(implicit ec: ExecutionContext, scheduler: Scheduler): Client = {
    implicit val s: Strategy = Strategy.fromExecutionContext(ec)

    def prepareLoop(req: Request, attempts: Int): Task[DisposableResponse] = {
      client.open(req).attempt flatMap {
        // TODO fs2 port - Reimplement request isIdempotent in some form
        case Right(dr @ DisposableResponse(Response(status, _, headers, _, _), _)) if RetriableStatuses(status) =>
          backoff(attempts) match {
            case Some(duration) =>
              logger.info(s"Request ${req} has failed on attempt #${attempts} with reason ${status}. Retrying after ${duration}.")
              dr.dispose.flatMap(_ => nextAttempt(req, attempts, duration, headers.get(`Retry-After`)))
            case None =>
              logger.info(s"Request ${req} has failed on attempt #${attempts} with reason ${status}. Giving up.")
              Task.now(dr)
          }
        case Right(dr) =>
          Task.now(dr)
        case Left(e) =>
          backoff(attempts) match {
            case Some(duration) =>
              logger.error(e)(s"Request ${req} threw an exception on attempt #${attempts} attempts. Retrying after ${duration}.")
              nextAttempt(req, attempts, duration, None)
            case None =>
              // info instead of error(e), because e is not discarded
              logger.info(s"Request ${req} threw an exception on attempt #${attempts} attempts. Giving up.")
              Task.fail(e)
          }
      }
    }

    def nextAttempt(req: Request, attempts: Int, duration: FiniteDuration, retryHeader: Option[`Retry-After`]): Task[DisposableResponse] = {
      val headerDuration = retryHeader.map { h =>
        h.retry match {
          case Left(d) => Instant.now().until(d.toInstant, ChronoUnit.SECONDS)
          case Right(secs) => secs
        }
      }.getOrElse(0L)
      val sleepDuration = Math.max(headerDuration, duration.length).seconds
      prepareLoop(req.withEmptyBody, attempts + 1).schedule(sleepDuration)
    }

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
