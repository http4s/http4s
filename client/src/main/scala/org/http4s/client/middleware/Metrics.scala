/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.middleware

import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.implicits._

import org.http4s.{Request, Response, Status}
import org.http4s.client.Client
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType.{Error, Timeout}

import scala.concurrent.TimeoutException

/** Client middleware to record metrics for the http4s client.
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

  /** Wraps a [[Client]] with a middleware capable of recording metrics
    *
    * @param ops a algebra describing the metrics operations
    * @param classifierF a function that allows to add a classifier that can be customized per request
    * @param client the [[Client]] to gather metrics from
    * @return the metrics middleware wrapping the [[Client]]
    */
  def apply[F[_]](
      ops: MetricsOps[F],
      classifierF: Request[F] => Option[String] = { (_: Request[F]) =>
        None
      })(client: Client[F])(implicit F: Temporal[F]): Client[F] =
    Client(withMetrics(client, ops, classifierF))

  private def withMetrics[F[_]](
      client: Client[F],
      ops: MetricsOps[F],
      classifierF: Request[F] => Option[String])(req: Request[F])(implicit
      F: Temporal[F]): Resource[F, Response[F]] =
    for {
      statusRef <- Resource.liftF(F.ref[Option[Status]](None))
      start <- Resource.liftF(F.monotonic)
      resp <- executeRequestAndRecordMetrics(
        client,
        ops,
        classifierF,
        req,
        statusRef,
        start.toNanos
      )
    } yield resp

  private def executeRequestAndRecordMetrics[F[_]](
      client: Client[F],
      ops: MetricsOps[F],
      classifierF: Request[F] => Option[String],
      req: Request[F],
      statusRef: Ref[F, Option[Status]],
      start: Long
  )(implicit F: Temporal[F]): Resource[F, Response[F]] =
    (for {
      _ <- Resource.make(ops.increaseActiveRequests(classifierF(req)))(_ =>
        ops.decreaseActiveRequests(classifierF(req)))
      _ <- Resource.make(F.unit) { _ =>
        F.monotonic
          .flatMap(now =>
            statusRef.get.flatMap(oStatus =>
              oStatus.traverse_(status =>
                ops.recordTotalTime(req.method, status, now.toNanos - start, classifierF(req)))))
      }
      resp <- client.run(req)
      _ <- Resource.liftF(statusRef.set(Some(resp.status)))
      end <- Resource.liftF(F.monotonic)
      _ <- Resource.liftF(ops.recordHeadersTime(req.method, end.toNanos - start, classifierF(req)))
    } yield resp).handleErrorWith { (e: Throwable) =>
      Resource.liftF(registerError(start, ops, classifierF(req))(e) *> F.raiseError[Response[F]](e))
    }

  private def registerError[F[_]](start: Long, ops: MetricsOps[F], classifier: Option[String])(
      e: Throwable)(implicit F: Temporal[F]): F[Unit] =
    F.monotonic
      .flatMap { now =>
        if (e.isInstanceOf[TimeoutException])
          ops.recordAbnormalTermination(now.toNanos - start, Timeout, classifier)
        else
          ops.recordAbnormalTermination(now.toNanos - start, Error(e), classifier)
      }
}
