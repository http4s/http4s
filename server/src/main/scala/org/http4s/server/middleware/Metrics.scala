/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.syntax.all._
import cats.effect.kernel.{Async, Outcome, Temporal}
import cats.syntax.all._
import fs2.Stream

import org.http4s._
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType.{Abnormal, Canceled, Error}

/** Server middleware to record metrics for the http4s server.
  *
  * This middleware will record:
  * - Number of active requests
  * - Time duration to send the response headers
  * - Time duration to send the whole response body
  * - Time duration of errors and other abnormal terminations
  *
  * This middleware can be extended to support any metrics ecosystem by implementing the [[MetricsOps]] type
  */
object Metrics {

  /** A server middleware capable of recording metrics
    *
    * @param ops a algebra describing the metrics operations
    * @param emptyResponseHandler an optional http status to be registered for requests that do not match
    * @param errorResponseHandler a function that maps a [[Throwable]] to an optional http status code to register
    * @param classifierF a function that allows to add a classifier that can be customized per request
    * @return the metrics middleware
    */
  def apply[F[_]](
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => Option[String] = { (_: Request[F]) =>
        None
      }
  )(routes: HttpRoutes[F])(implicit F: Async[F]): HttpRoutes[F] =
    Kleisli(
      metricsService[F](ops, routes, emptyResponseHandler, errorResponseHandler, classifierF)(_))

  private def metricsService[F[_]](
      ops: MetricsOps[F],
      routes: HttpRoutes[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
      classifierF: Request[F] => Option[String]
  )(req: Request[F])(implicit F: Async[F]): OptionT[F, Response[F]] =
    OptionT {
      for {
        initialTime <- F.monotonic
        decreaseActiveRequestsOnce <- decreaseActiveRequestsAtMostOnce(ops, classifierF(req))
        result <-
          F.bracketCase(ops.increaseActiveRequests(classifierF(req))) { _ =>
            for {
              responseOpt <- routes(req).value
              headersElapsed <- F.monotonic
              result <- responseOpt.fold(
                onEmpty[F](
                  req.method,
                  initialTime.toNanos,
                  headersElapsed.toNanos,
                  ops,
                  emptyResponseHandler,
                  classifierF(req),
                  decreaseActiveRequestsOnce)
                  .as(Option.empty[Response[F]])
              )(
                onResponse(
                  req.method,
                  initialTime.toNanos,
                  headersElapsed.toNanos,
                  ops,
                  classifierF(req),
                  decreaseActiveRequestsOnce)(_).some
                  .pure[F]
              )
            } yield result
          } {
            case (_, Outcome.Succeeded(_)) => F.unit
            case (_, Outcome.Errored(e)) =>
              for {
                headersElapsed <- F.monotonic
                out <- onServiceError(
                  req.method,
                  initialTime.toNanos,
                  headersElapsed.toNanos,
                  ops,
                  errorResponseHandler(e),
                  classifierF(req),
                  e
                ) *> decreaseActiveRequestsOnce
              } yield out
            case (_, Outcome.Canceled()) =>
              onServiceCanceled(
                initialTime.toNanos,
                ops,
                classifierF(req)
              ) *> decreaseActiveRequestsOnce
          }
      } yield result
    }

  private def onEmpty[F[_]](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status],
      classifier: Option[String],
      decreaseActiveRequestsOnce: F[Unit]
  )(implicit F: Temporal[F]): F[Unit] =
    (for {
      now <- F.monotonic
      _ <- emptyResponseHandler.traverse_(status =>
        ops.recordHeadersTime(method, headerTime - start, classifier) *>
          ops.recordTotalTime(method, status, now.toNanos - start, classifier))
    } yield ()).guarantee(decreaseActiveRequestsOnce)

  private def onResponse[F[_]](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      classifier: Option[String],
      decreaseActiveRequestsOnce: F[Unit]
  )(r: Response[F])(implicit F: Temporal[F]): Response[F] = {
    val newBody = r.body
      .onFinalize {
        for {
          now <- F.monotonic
          _ <- ops.recordHeadersTime(method, headerTime - start, classifier)
          _ <- ops.recordTotalTime(method, r.status, now.toNanos - start, classifier)
          _ <- decreaseActiveRequestsOnce
        } yield {}
      }
      .handleErrorWith(e =>
        for {
          now <- Stream.eval(F.monotonic)
          _ <- Stream.eval(
            ops.recordAbnormalTermination(now.toNanos - start, Abnormal(e), classifier))
          r <- Stream.raiseError[F](e)
        } yield r)
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      errorResponseHandler: Option[Status],
      classifier: Option[String],
      error: Throwable
  )(implicit F: Temporal[F]): F[Unit] =
    for {
      now <- F.monotonic
      _ <- errorResponseHandler.traverse_(status =>
        ops.recordHeadersTime(method, headerTime - start, classifier) *>
          ops.recordTotalTime(method, status, now.toNanos - start, classifier) *>
          ops.recordAbnormalTermination(now.toNanos - start, Error(error), classifier))
    } yield ()

  private def onServiceCanceled[F[_]](
      start: Long,
      ops: MetricsOps[F],
      classifier: Option[String]
  )(implicit F: Temporal[F]): F[Unit] =
    for {
      now <- F.monotonic
      _ <- ops.recordAbnormalTermination(now.toNanos - start, Canceled, classifier)
    } yield ()

  private def decreaseActiveRequestsAtMostOnce[F[_]](
      ops: MetricsOps[F],
      classifier: Option[String]
  )(implicit F: Async[F]): F[F[Unit]] =
    F.ref(false)
      .map { ref =>
        ref.getAndSet(true).bracket(_ => F.unit) {
          case false => ops.decreaseActiveRequests(classifier)
          case _ => F.unit
        }
      }
}
