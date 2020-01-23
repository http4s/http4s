package org.http4s.client.middleware

import cats.effect.{Clock, Resource, Sync}
import cats.implicits._
import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import org.http4s.{Request, Response, Status}
import org.http4s.client.Client
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType.{Error, Timeout}

import scala.concurrent.TimeoutException

/**
  * Client middleware to record metrics for the http4s client.
  *
  * This middleware will record:
  * - Number of active requests
  * - Time duration to receive the response headers
  * - Time duration to process the whole response body
  * - Time duration of errors, timeouts and other abnormal terminations
  *
  * This middleware can be extended to support any metrics ecosystem by implementing the [[MetricsOps]] type
  */
object Metrics {

  /**
    * Wraps a [[Client]] with a middleware capable of recording metrics
    *
    * @param ops a algebra describing the metrics operations
    * @param classifierF a function that allows to add a classifier that can be customized per request
    * @param client the [[Client]] to gather metrics from
    * @return the metrics middleware wrapping the [[Client]]
    */
  def apply[F[_]](ops: MetricsOps[F], classifierF: Request[F] => Option[String] = { _: Request[F] =>
    None
  })(client: Client[F])(implicit F: Sync[F], clock: Clock[F]): Client[F] =
    Client(withMetrics(client, ops, classifierF))

  private def withMetrics[F[_]](
      client: Client[F],
      ops: MetricsOps[F],
      classifierF: Request[F] => Option[String])(
      req: Request[F])(implicit F: Sync[F], clock: Clock[F]): Resource[F, Response[F]] =
    (for {
      statusRef <- Resource.liftF(Ref.of[F, Option[Status]](None))
      start <- Resource.liftF(clock.monotonic(TimeUnit.NANOSECONDS))
      _ <- Resource.make(ops.increaseActiveRequests(classifierF(req)))(_ =>
        ops.decreaseActiveRequests(classifierF(req)))
      _ <- Resource.make(F.unit) { _ =>
        clock
          .monotonic(TimeUnit.NANOSECONDS)
          .flatMap(now =>
            statusRef.get.flatMap(oStatus =>
              oStatus.traverse_(status =>
                ops.recordTotalTime(req.method, status, now - start, classifierF(req)))))
      }
      resp <- client.run(req)
      _ <- Resource.liftF(statusRef.set(Some(resp.status)))
      end <- Resource.liftF(clock.monotonic(TimeUnit.NANOSECONDS))
      _ <- Resource.liftF(ops.recordHeadersTime(req.method, end - start, classifierF(req)))
    } yield resp)
      .handleErrorWith { e: Throwable =>
        Resource.liftF[F, Response[F]](
          registerError(ops, classifierF(req))(e) *> F.raiseError[Response[F]](e)
        )
      }

  private def registerError[F[_]](ops: MetricsOps[F], classifier: Option[String])(
      e: Throwable): F[Unit] =
    if (e.isInstanceOf[TimeoutException]) {
      ops.recordAbnormalTermination(1, Timeout, classifier)
    } else {
      ops.recordAbnormalTermination(1, Error, classifier)
    }
}
