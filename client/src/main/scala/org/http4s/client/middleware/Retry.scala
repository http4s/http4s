package org.http4s
package client
package middleware

import org.http4s.Status.ResponseClass.Successful
import org.http4s.EmptyBody
import org.log4s.getLogger

import scala.concurrent.duration._
import scala.math.{pow, min, random}

import scalaz.concurrent.Task


object Retry {
 
  private[this] val logger = getLogger

  def apply(backoff: Int => Option[FiniteDuration])(client: Client) = new Client {

    def shutdown(): Task[Unit] = client.shutdown()

    def open(req: Request): Task[DisposableResponse] =
      loop(req, 1)

    private def loop(req: Request, attempts: Int): Task[DisposableResponse] = {
      client.open(req) flatMap {
        case dr @ DisposableResponse(Successful(resp), _) =>
          Task.now(dr)
        case fail =>
          logger.info(s"Request ${req} has failed attempt ${attempts} with reason ${fail}")
          fail.dispose.flatMap { _ =>
            backoff(attempts).fold(Task.now(fail))(dur => nextAttempt(req, attempts, dur))
          }
      }
    }

    private def nextAttempt(req: Request, attempts: Int, duration: FiniteDuration): Task[DisposableResponse] =
      Task.async { (loop(req.copy(body = EmptyBody), attempts + 1).get after duration).runAsync }
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
