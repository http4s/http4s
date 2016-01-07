package org.http4s
package client
package middleware

import org.http4s.Status.ResponseClass.Successful
import org.http4s.{Response, Request, EmptyBody}
import org.http4s.client.Client
import org.log4s.getLogger

import scala.concurrent.duration._
import scala.math.{pow, min, random}
import scalaz.Kleisli

import scalaz.concurrent.Task


object Retry {
 
  private[this] val logger = getLogger

  def apply(backoff: Int => Option[FiniteDuration])(client: Client) = {
    def prepareLoop(req: Request, attempts: Int): Task[DisposableResponse] = {
      client.open(req) flatMap {
        case dr @ DisposableResponse(Successful(resp), _) =>
          Task.now(dr)
        case dr @ DisposableResponse(Response(status, _, _, _, _), _) =>
          logger.info(s"Request ${req} has failed attempt ${attempts} with reason ${status}")
          backoff(attempts).fold(Task.now(dr))(dur => nextAttempt(req, attempts, dur))
      }
    }

    def nextAttempt(req: Request, attempts: Int, duration: FiniteDuration): Task[DisposableResponse] =
      Task.async { (prepareLoop(req.copy(body = EmptyBody), attempts + 1).get after duration).runAsync }

    client.copy(open = Service.lift(prepareLoop(_, 1)))
  }
}

object RetryPolicy {

  def exponentialBackoff(maxWait: Duration, maxRetry: Int): Int => Option[FiniteDuration] = {
    val maxInMillis = maxWait.toMillis
    k => if (k > maxRetry) None else Some(expBackoff(k, maxInMillis))
  }

  private def expBackoff(k: Int, maxInMillis: Long): FiniteDuration = {
    val millis = (pow(2, k) - 1) * 1000
    val interval = min(millis, maxInMillis)
    FiniteDuration((random * interval).toLong, MILLISECONDS)
  }
}
