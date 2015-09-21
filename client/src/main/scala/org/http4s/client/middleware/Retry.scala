package org.http4s
package client
package middleware

import org.http4s.Status.ResponseClass.Successful
import org.http4s.{Response, Request, EmptyBody}
import org.http4s.client.Client
import scala.concurrent.duration._
import scala.math.{pow, min, random}
import scalaz.concurrent.Task
import scala.language.postfixOps

object Retry {

  def apply(backoff: Task[Int => Option[FiniteDuration]])(client: Client) = new Client {

    def shutdown(): Task[Unit] = client.shutdown()

    def prepare(req: Request): Task[Response] = prepareLoop(req, 1)

    private def prepareLoop(req: Request, attempts: Int): Task[Response] = {
      client.prepare(req) flatMap {
        case Successful(resp) => Task.now(resp)
        case fail => backoff flatMap { f =>
          f(attempts).fold(Task.now(fail))(dur => nextAttempt(req, attempts, dur))
        }
      }
    }

    private def nextAttempt(req: Request, attempts: Int, duration: FiniteDuration): Task[Response] =
      Task.async { prepareLoop(req.copy(body = EmptyBody), attempts + 1).get after duration runAsync }
  }
}

object RetryPolicy {

  def exponentialBackoff(maxWait: Duration, maxRetry: Int): Task[Int => Option[FiniteDuration]] = {
    val maxInMillis = maxWait.toMillis
    Task.delay(k =>
      if (k > maxRetry) None
      else Some(expBackoff(k, maxInMillis))
    )
  }

  private def expBackoff(k: Int, maxInMillis: Long): FiniteDuration = {
    val millis = (pow(2, k) - 1) * 1000
    val interval = min(millis, maxInMillis)
    FiniteDuration((random * interval).toLong, MILLISECONDS)
  }
}
