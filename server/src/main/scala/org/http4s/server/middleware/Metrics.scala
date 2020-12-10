/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.server.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.{Clock, ExitCase, Sync}
import cats.syntax.all._
import fs2.Stream
import java.util.concurrent.TimeUnit
import org.http4s._
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType.{Abnormal, Error}

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
  )(routes: HttpRoutes[F])(implicit F: Sync[F], clock: Clock[F]): HttpRoutes[F] =
    Kleisli(
      metricsService[F](ops, routes, emptyResponseHandler, errorResponseHandler, classifierF)(_))

  private def metricsService[F[_]: Sync](
      ops: MetricsOps[F],
      routes: HttpRoutes[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
      classifierF: Request[F] => Option[String]
  )(req: Request[F])(implicit clock: Clock[F]): OptionT[F, Response[F]] =
    OptionT {
      for {
        initialTime <- clock.monotonic(TimeUnit.NANOSECONDS)
        decreaseActiveRequestsOnce <- decreaseActiveRequestsAtMostOnce(ops, classifierF(req))
        result <-
          ops
            .increaseActiveRequests(classifierF(req))
            .bracketCase { _ =>
              for {
                responseOpt <- routes(req).value
                headersElapsed <- clock.monotonic(TimeUnit.NANOSECONDS)
                result <- responseOpt.fold(
                  onEmpty[F](
                    req.method,
                    initialTime,
                    headersElapsed,
                    ops,
                    emptyResponseHandler,
                    classifierF(req),
                    decreaseActiveRequestsOnce)
                    .as(Option.empty[Response[F]])
                )(
                  onResponse(
                    req.method,
                    initialTime,
                    headersElapsed,
                    ops,
                    classifierF(req),
                    decreaseActiveRequestsOnce)(_).some
                    .pure[F]
                )
              } yield result
            } {
              case (_, ExitCase.Completed) => Sync[F].unit
              case (_, ExitCase.Canceled) =>
                onServiceCanceled(
                  initialTime,
                  ops,
                  classifierF(req)
                ) *> decreaseActiveRequestsOnce
              case (_, ExitCase.Error(e)) =>
                for {
                  headersElapsed <- clock.monotonic(TimeUnit.NANOSECONDS)
                  out <- onServiceError(
                    req.method,
                    initialTime,
                    headersElapsed,
                    ops,
                    errorResponseHandler(e),
                    classifierF(req)
                  ) *> decreaseActiveRequestsOnce
                } yield out
            }
      } yield result
    }

  private def onEmpty[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status],
      classifier: Option[String],
      decreaseActiveRequestsOnce: F[Unit]
  )(implicit clock: Clock[F]): F[Unit] =
    (for {
      now <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- emptyResponseHandler.traverse_(status =>
        ops.recordHeadersTime(method, headerTime - start, classifier) *>
          ops.recordTotalTime(method, status, now - start, classifier))
    } yield ()).guarantee(decreaseActiveRequestsOnce)

  private def onResponse[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      classifier: Option[String],
      decreaseActiveRequestsOnce: F[Unit]
  )(r: Response[F])(implicit clock: Clock[F]): Response[F] = {
    val newBody = r.body
      .onFinalize {
        for {
          now <- clock.monotonic(TimeUnit.NANOSECONDS)
          _ <- ops.recordHeadersTime(method, headerTime - start, classifier)
          _ <- ops.recordTotalTime(method, r.status, now - start, classifier)
          _ <- decreaseActiveRequestsOnce
        } yield {}
      }
      .handleErrorWith(e =>
        for {
          now <- Stream.eval(clock.monotonic(TimeUnit.NANOSECONDS))
          _ <- Stream.eval(ops.recordAbnormalTermination(now - start, Abnormal, classifier))
          r <- Stream.raiseError[F](e)
        } yield r)
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      errorResponseHandler: Option[Status],
      classifier: Option[String]
  )(implicit clock: Clock[F]): F[Unit] =
    for {
      now <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- errorResponseHandler.traverse_(status =>
        ops.recordHeadersTime(method, headerTime - start, classifier) *>
          ops.recordTotalTime(method, status, now - start, classifier) *>
          ops.recordAbnormalTermination(now - start, Error, classifier))
    } yield ()

  private def onServiceCanceled[F[_]: Sync](
      start: Long,
      ops: MetricsOps[F],
      classifier: Option[String]
  )(implicit clock: Clock[F]): F[Unit] =
    for {
      now <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- ops.recordAbnormalTermination(now - start, Abnormal, classifier)
    } yield ()

  private def decreaseActiveRequestsAtMostOnce[F[_]](
      ops: MetricsOps[F],
      classifier: Option[String]
  )(implicit F: Sync[F]): F[F[Unit]] =
    Ref
      .of(false)
      .map((ref: Ref[F, Boolean]) =>
        ref.getAndSet(true).bracket(_ => F.unit) {
          case false => ops.decreaseActiveRequests(classifier)
          case _ => F.unit
        })
}
