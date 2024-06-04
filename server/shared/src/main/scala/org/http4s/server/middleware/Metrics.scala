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

import cats.data.Kleisli
import cats.effect.Clock
import cats.effect.kernel.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.metrics.{CustomMetricsOps, MetricsOps, TerminationType}
import org.http4s.metrics.TerminationType.Abnormal
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TerminationType.Error
import org.http4s.util.{SizedSeq, SizedSeq0}

/** Server middleware to record metrics for the http4s server.
  *
  * This middleware will record:
  * - Number of active requests
  * - Time duration to send the response headers
  * - Time duration to send the whole response body
  * - Time duration of errors and other abnormal terminations
  *
  * This middleware can be extended to support any metrics ecosystem by implementing the [[org.http4s.metrics.MetricsOps]] type
  */
object Metrics {

  private[this] final case class MetricsEntry(
      method: Method,
      startTime: Long,
      classifier: Option[String],
  )

  /** A server middleware capable of recording metrics
    *
    * @param ops a algebra describing the metrics operations
    * @param emptyResponseHandler an optional http status to be registered for requests that do not match
    * @param errorResponseHandler a function that maps a [[java.lang.Throwable]] to an optional http status code to register
    * @param classifierF a function that allows to add a classifier that can be customized per request
    * @return the metrics middleware
    */
  def apply[F[_]](
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => Option[String] = { (_: Request[F]) =>
        None
      },
  )(routes: HttpRoutes[F])(implicit F: Clock[F], C: MonadCancel[F, Throwable]): HttpRoutes[F] =
    effect[F](ops, emptyResponseHandler, errorResponseHandler, classifierF(_).pure[F])(routes)

  def withCustomLabels[F[_], SL <: SizedSeq[String]](
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => Option[String] = { (_: Request[F]) =>
        None
      },
  )(routes: HttpRoutes[F])(implicit F: Clock[F], C: MonadCancel[F, Throwable]): HttpRoutes[F] =
    effectWithCustomLabels[F, SL](
      ops,
      customLabelValues,
      emptyResponseHandler,
      errorResponseHandler,
      classifierF(_).pure[F],
    )(routes)

  /** A server middleware capable of recording metrics
    *
    * Same as [[apply]], but can classify requests effectually, e.g. performing side-effects.
    * Failed attempt to classify the request (e.g. failing with `F.raiseError`) leads to not recording metrics for that request.
    *
    * @note Compiling the request body in `classifierF` is unsafe, unless you are using some caching middleware.
    *
    * @param ops a algebra describing the metrics operations
    * @param emptyResponseHandler an optional http status to be registered for requests that do not match
    * @param errorResponseHandler a function that maps a [[java.lang.Throwable]] to an optional http status code to register
    * @param classifierF a function that allows to add a classifier that can be customized per request
    * @return the metrics middleware
    */
  def effect[F[_]](
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => F[Option[String]],
  )(routes: HttpRoutes[F])(implicit F: Clock[F], C: MonadCancel[F, Throwable]): HttpRoutes[F] = {
    val cops = CustomMetricsOps.fromMetricsOps(ops)
    val emptyCustomLabelValues = SizedSeq0[String]()
    effectWithCustomLabels(
      cops,
      emptyCustomLabelValues,
      emptyResponseHandler,
      errorResponseHandler,
      classifierF,
    )(routes)

  }

  def effectWithCustomLabels[F[_], SL <: SizedSeq[String]](
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => F[Option[String]],
  )(routes: HttpRoutes[F])(implicit F: Clock[F], C: MonadCancel[F, Throwable]): HttpRoutes[F] = {
    def startMetrics(request: Request[F]): F[ContextRequest[F, MetricsEntry]] =
      for {
        classifier <- classifierF(request)
        _ <- ops.increaseActiveRequests(classifier, customLabelValues)
        startTime <- F.monotonic
      } yield ContextRequest(MetricsEntry(request.method, startTime.toNanos, classifier), request)

    def stopMetrics(metrics: MetricsEntry): F[Long] =
      // Decrease active requests _first_ in case any of the other effects triggers an error.
      // This differs from the < 0.21.14 semantics, which decreased it _after_ the other effects.
      // This may have caused the bugs that reported the active requests counter to have drifted.
      for {
        _ <- ops.decreaseActiveRequests(metrics.classifier, customLabelValues)
        endTime <- F.monotonic
      } yield endTime.toNanos - metrics.startTime

    def metricHeaders(metrics: MetricsEntry, resp: Response[F]): F[ContextResponse[F, Status]] =
      for {
        now <- F.monotonic
        headerTime = now.toNanos - metrics.startTime
        _ <- ops.recordHeadersTime(metrics.method, headerTime, metrics.classifier, customLabelValues)
      } yield ContextResponse(resp.status, resp)

    BracketRequestResponse.bracketRequestResponseCaseRoutes_[F, MetricsEntry, Status] {
      startMetrics
    } { case (metrics, maybeStatus, outcome) =>
      stopMetrics(metrics).flatMap { totalTime =>
        def recordTotal(status: Status): F[Unit] =
          ops.recordTotalTime(metrics.method, status, totalTime, metrics.classifier, customLabelValues)

        def recordAbnormal(term: TerminationType): F[Unit] =
          ops.recordAbnormalTermination(totalTime, term, metrics.classifier, customLabelValues)

        (outcome, maybeStatus) match {
          case (Outcome.Succeeded(_), None) => emptyResponseHandler.traverse_(recordTotal)

          case (Outcome.Succeeded(_), Some(status)) => recordTotal(status)

          case (Outcome.Errored(e), None) =>
            // If an error occurred, and the status is empty, this means
            // the error occurred before the routes could generate a response.
            ops.recordHeadersTime(metrics.method, totalTime, metrics.classifier, customLabelValues) *>
              recordAbnormal(Error(e)) *>
              errorResponseHandler(e).traverse_(recordTotal)

          case (Outcome.Errored(e), Some(status)) =>
            // If an error occurred, but the status is non-empty, this means
            // the error occurred during the stream processing of the response body.
            // In which case recordHeadersTime was invoked in the normal manner,
            // so we do not need to invoke it here.
            recordAbnormal(Abnormal(e)) *> recordTotal(status)

          case (Outcome.Canceled(), _) => recordAbnormal(Canceled)
        }
      }
    }(C)(
      Kleisli { case ContextRequest(metrics, req) =>
        routes(req).semiflatMap(metricHeaders(metrics, _))
      }
    )
  }

}
