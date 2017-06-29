package org.http4s
package client
package middleware

import cats._
import cats.implicits._
import org.http4s.Status._
import org.log4s.getLogger

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

  def apply[F[_]](backoff: Int => Option[FiniteDuration])
                 (client: Client[F])
                 (implicit F: MonadError[F, Throwable]): Client[F] = {
    def prepareLoop(req: Request[F], attempts: Int): F[DisposableResponse[F]] = {
      client.open(req).attempt.flatMap {
        // TODO fs2 port - Reimplement request isIdempotent in some form
        case Right(dr @ DisposableResponse(Response(status, _, _, _, _), _)) if RetriableStatuses(status) =>
          backoff(attempts) match {
            case Some(duration) =>
              logger.info(s"Request $req has failed on attempt #$attempts with reason $status. Retrying after $duration.")
              dr.dispose.flatMap(_ => nextAttempt(req, attempts, duration))
            case None =>
              logger.info(s"Request $req has failed on attempt #$attempts with reason $status. Giving up.")
              F.pure(dr)
          }
        case Right(dr) =>
          F.pure(dr)
        case Left(e) =>
          backoff(attempts) match {
            case Some(duration) =>
              logger.error(e)(s"Request $req threw an exception on attempt #$attempts attempts. Retrying after $duration.")
              nextAttempt(req, attempts, duration)
            case None =>
              // info instead of error(e), because e is not discarded
              logger.info(s"Request $req threw an exception on attempt #$attempts attempts. Giving up.")
              F.raiseError[DisposableResponse[F]](e)
          }
      }
    }

    def nextAttempt(req: Request[F], attempts: Int, duration: FiniteDuration): F[DisposableResponse[F]] = {
        prepareLoop(req.withBody(EmptyBody), attempts + 1)
    }
      // TODO honor Retry-After header
      // Task.async { (prepareLoop(req.copy(body = EmptyBody), attempts + 1)) }

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
