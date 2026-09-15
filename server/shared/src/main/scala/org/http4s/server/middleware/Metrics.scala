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
import cats.effect.kernel._
import cats.syntax.all._
import org.http4s._
import org.http4s.metrics.CustomMetricsOps
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.MetricsOps2
import org.http4s.metrics.MetricsRequest
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Abnormal
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TerminationType.Error
import org.http4s.util.SizedSeq
import org.http4s.util.SizedSeq0

import scala.concurrent.duration.FiniteDuration

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

  // The fromInt can't fail, but being more safe than adding a yolo here.
  // 499 is used as "client closed request" code in nginx, which seems the closest thing.
  val CanceledStatus: Status = Status.fromInt(499).getOrElse(Status.InternalServerError)

  private[this] final case class MetricsEntry(
      method: Method,
      startTime: Long,
      classifier: Option[String],
  )

  private[this] final case class MetricsEntry2[F[_], Context](
      request: MetricsRequest,
      startTime: FiniteDuration,
      context: Context,
      requestBodySizeRef: Ref[F, Long],
      responseBodySizeRef: Ref[F, Long],
  )

  /** A server middleware capable of recording metrics
    *
    * @param ops a algebra describing the metrics operations
    * @param emptyResponseHandler an optional http status to be registered for requests that do not match
    * @param errorResponseHandler a function that maps a [[java.lang.Throwable]] to an optional http status code to register.
    *        Returning `None` excludes the request from [[org.http4s.metrics.MetricsOps.recordTotalTime]], and therefore
    *        from the backend's request counter. The abnormal termination is recorded either way.
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

  /** A server middleware capable of recording metrics.
    *
    * @note Middleware ordering defines the scope of the measurements. It is generally useful to
    * place metrics outside other middleware so requests handled or rejected there are recorded.
    * Body sizes are counted from the streams observed at this layer: for example,
    * `Metrics(ops)(GZip(routes))` records the encoded, transport-facing response, whereas
    * `GZip(Metrics(ops)(routes))` records the uncompressed, application-facing response.
    */
  def apply[F[_]](
      ops: MetricsOps2[F]
  )(routes: HttpRoutes[F])(implicit F: Temporal[F]): HttpRoutes[F] =
    withMetrics2(
      ops,
      Status.NotFound.some,
      (_: Throwable) => Status.InternalServerError.some,
    )(routes)

  /** A server middleware capable of recording metrics.
    *
    * @note Middleware ordering defines the scope of the measurements. It is generally useful to
    * place metrics outside other middleware so requests handled or rejected there are recorded.
    * Body sizes are counted from the streams observed at this layer: for example,
    * `Metrics(ops)(GZip(routes))` records the encoded, transport-facing response, whereas
    * `GZip(Metrics(ops)(routes))` records the uncompressed, application-facing response.
    */
  def apply[F[_]](
      ops: MetricsOps2[F],
      emptyResponseHandler: Option[Status],
  )(routes: HttpRoutes[F])(implicit F: Temporal[F]): HttpRoutes[F] =
    withMetrics2(
      ops,
      emptyResponseHandler,
      (_: Throwable) => Status.InternalServerError.some,
    )(routes)

  /** A server middleware capable of recording metrics.
    *
    * @note Middleware ordering defines the scope of the measurements. It is generally useful to
    * place metrics outside other middleware so requests handled or rejected there are recorded.
    * Body sizes are counted from the streams observed at this layer: for example,
    * `Metrics(ops)(GZip(routes))` records the encoded, transport-facing response, whereas
    * `GZip(Metrics(ops)(routes))` records the uncompressed, application-facing response.
    */
  def apply[F[_]](
      ops: MetricsOps2[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
  )(routes: HttpRoutes[F])(implicit F: Temporal[F]): HttpRoutes[F] =
    withMetrics2(ops, emptyResponseHandler, errorResponseHandler)(routes)

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
    * @param errorResponseHandler a function that maps a [[java.lang.Throwable]] to an optional http status code to register.
    *        Returning `None` excludes the request from [[org.http4s.metrics.MetricsOps.recordTotalTime]], and therefore
    *        from the backend's request counter. The abnormal termination is recorded either way.
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
        _ <- ops.recordHeadersTime(
          metrics.method,
          headerTime,
          metrics.classifier,
          customLabelValues,
        )
      } yield ContextResponse(resp.status, resp)

    BracketRequestResponse.bracketRequestResponseCaseRoutes_[F, MetricsEntry, Status] {
      startMetrics
    } { case (metrics, maybeStatus, outcome) =>
      stopMetrics(metrics).flatMap { totalTime =>
        def recordTotal(status: Status): F[Unit] =
          ops.recordTotalTime(
            metrics.method,
            status,
            totalTime,
            metrics.classifier,
            customLabelValues,
          )

        def recordAbnormal(term: TerminationType): F[Unit] =
          ops.recordAbnormalTermination(totalTime, term, metrics.classifier, customLabelValues)

        (outcome, maybeStatus) match {
          case (Outcome.Succeeded(_), None) => emptyResponseHandler.traverse_(recordTotal)

          case (Outcome.Succeeded(_), Some(status)) => recordTotal(status)

          case (Outcome.Errored(e), None) =>
            // No response, so no headers were sent. recordHeadersTime is skipped rather than
            // called with the elapsed time, which would be a sample for a send that never happened.
            recordAbnormal(Error(e)) *> errorResponseHandler(e).traverse_(recordTotal)

          case (Outcome.Errored(e), Some(status)) =>
            // If an error occurred, but the status is non-empty, this means
            // the error occurred during the stream processing of the response body.
            // In which case recordHeadersTime was invoked in the normal manner,
            // so we do not need to invoke it here.
            recordAbnormal(Abnormal(e)) *> recordTotal(status)

          case (Outcome.Canceled(), maybeStatus) =>
            // recordTotal as well as recordAbnormal, so canceled requests reach the backend's
            // counter. `maybeStatus` is defined only when cancellation happened mid-body, in which
            // case that status really was sent; otherwise CanceledStatus stands in.
            recordAbnormal(Canceled) *> recordTotal(maybeStatus.getOrElse(CanceledStatus))
        }
      }
    }(C)(
      Kleisli { case ContextRequest(metrics, req) =>
        routes(req).semiflatMap(metricHeaders(metrics, _))
      }
    )
  }

  private def withMetrics2[F[_]](
      ops: MetricsOps2[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
  )(routes: HttpRoutes[F])(implicit F: Temporal[F]): HttpRoutes[F] = {
    def countBodyBytes(body: EntityBody[F], sizeRef: Ref[F, Long]): EntityBody[F] =
      fs2.Stream.suspend {
        var size = 0L
        body
          .mapChunks { chunk =>
            size += chunk.size.toLong
            chunk
          }
          .onFinalize(sizeRef.update(_ + size))
      }

    def startMetrics(request: Request[F]): F[ContextRequest[F, MetricsEntry2[F, ops.Context]]] = {
      val metricsRequest = MetricsRequest.fromRequest(request)
      for {
        startTime <- F.monotonic
        context <- ops.createContext(metricsRequest)
        requestBodySizeRef <- F.ref(0L)
        responseBodySizeRef <- F.ref(0L)
        contextRequest <- F.uncancelable { _ =>
          for {
            _ <- ops.increaseActiveRequests(metricsRequest, context)
            requestWithMetrics = request.withBodyStream(
              countBodyBytes(request.body, requestBodySizeRef)
            )
          } yield ContextRequest(
            MetricsEntry2(
              metricsRequest,
              startTime,
              context,
              requestBodySizeRef,
              responseBodySizeRef,
            ),
            requestWithMetrics,
          )
        }
      } yield contextRequest
    }

    def stopMetrics(metrics: MetricsEntry2[F, ops.Context]): F[FiniteDuration] =
      for {
        endTime <- F.monotonic
        _ <- ops.decreaseActiveRequests(metrics.request, metrics.context)
      } yield endTime - metrics.startTime

    def metricHeaders(
        metrics: MetricsEntry2[F, ops.Context],
        response: Response[F],
    ): F[ContextResponse[F, ResponsePrelude]] =
      for {
        now <- F.monotonic
        _ <- ops.recordHeadersTime(
          metrics.request,
          now - metrics.startTime,
          metrics.context,
        )
        prelude = response.responsePrelude
        respWithMetrics = response.withBodyStream(
          countBodyBytes(response.body, metrics.responseBodySizeRef)
        )
      } yield ContextResponse(prelude, respWithMetrics)

    BracketRequestResponse
      .bracketRequestResponseCaseRoutes_[F, MetricsEntry2[F, ops.Context], ResponsePrelude](
        (request: Request[F]) => startMetrics(request)
      ) { case (metrics, maybeResponse, outcome) =>
        stopMetrics(metrics).flatMap { totalTime =>
          def recordTotal(
              response: Option[ResponsePrelude],
              terminationType: Option[TerminationType],
          ): F[Unit] =
            for {
              _ <- ops.recordTotalTime(
                metrics.request,
                response,
                terminationType,
                totalTime,
                metrics.context,
              )
              requestBodySize <- metrics.requestBodySizeRef.get
              _ <- ops.recordRequestBodySize(
                metrics.request,
                response,
                terminationType,
                requestBodySize,
                metrics.context,
              )
              _ <- response.fold(F.unit) { resp =>
                for {
                  responseBodySize <- metrics.responseBodySizeRef.get
                  _ <- ops.recordResponseBodySize(
                    metrics.request,
                    resp,
                    terminationType,
                    responseBodySize,
                    metrics.context,
                  )
                } yield ()
              }
            } yield ()

          (outcome, maybeResponse) match {
            case (Outcome.Succeeded(_), None) =>
              emptyResponseHandler.fold(F.unit)(_ => recordTotal(None, None))
            case (Outcome.Succeeded(_), Some(response)) =>
              recordTotal(Some(response), None)
            case (Outcome.Errored(e), None) =>
              errorResponseHandler(e).fold(F.unit)(_ => recordTotal(None, Some(Error(e))))
            case (Outcome.Errored(e), Some(response)) =>
              recordTotal(Some(response), Some(Abnormal(e)))
            case (Outcome.Canceled(), None) => recordTotal(None, Some(Canceled))
            case (Outcome.Canceled(), Some(response)) =>
              recordTotal(Some(response), Some(Canceled))
          }
        }
      }(F)(Kleisli { case ContextRequest(metrics, request) =>
        routes(request).semiflatMap(metricHeaders(metrics, _))
      })
  }

}
