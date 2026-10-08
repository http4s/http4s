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
import org.http4s.headers.`Content-Length`
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
      requestBodySizeRef: Ref[F, Option[Long]],
      responseBodySizeRef: Ref[F, Option[Long]],
      completionStartedRef: Ref[F, Boolean],
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

  /** Records metrics for an [[HttpApp]].
    *
    * @note Without `Content-Length`, count whole chunks read from the body, including a chunk
    * that the caller only partly uses. With `Content-Length`, record the header value only after
    * the stream ends and its finalizers succeed. Otherwise, skip the size metric. HEAD responses
    * and statuses that cannot have a body record zero when metrics complete. A socket write can
    * fail after bytes have been counted. See [[org.http4s.metrics.MetricsOps2]] for details.
    *
    * Apply routing fallbacks, error handling, compression, and other body changes before
    * wrapping the app with `Metrics`. Pass that app to the server. This counts compressed
    * response bytes. Read the response body once. If it is read again, completion metrics
    * are still attempted only once.
    *
    * @example
    * {{{
    * val routes: HttpRoutes[F] = ???
    * val app = GZip(routes).orNotFound
    * val measuredApp = Metrics(ops)(app)
    * }}}
    */
  def apply[F[_]](ops: MetricsOps2[F])(app: HttpApp[F])(implicit F: Temporal[F]): HttpApp[F] =
    withMetrics2(ops)(app)

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
    * Same as `apply`, but can classify requests effectually, e.g. performing side-effects.
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
      ops: MetricsOps2[F]
  )(app: HttpApp[F])(implicit F: Temporal[F]): HttpApp[F] = {
    def countBodyBytes(body: EntityBody[F], sizeRef: Ref[F, Option[Long]]): EntityBody[F] =
      fs2.Stream.suspend {
        var size = 0L
        body
          .mapChunks { chunk =>
            size += chunk.size.toLong
            chunk
          }
          .onFinalize(sizeRef.update(_.orElse(Some(size))))
      }

    def measureBodyBytes(
        body: EntityBody[F],
        contentLength: Option[Long],
        sizeRef: Ref[F, Option[Long]],
    ): EntityBody[F] =
      // Close body-owned cleanup before considering a declared length complete.
      contentLength.fold(countBodyBytes(body.scope, sizeRef)) { declaredSize =>
        body.scope ++ fs2.Stream.exec(sizeRef.update(_.orElse(Some(declaredSize))))
      }

    def startMetrics(
        request: Request[F],
        metricsRequest: MetricsRequest,
        context: ops.Context,
    ): F[ContextRequest[F, MetricsEntry2[F, ops.Context]]] =
      for {
        startTime <- F.monotonic
        requestBodySizeRef <- F.ref(Option.empty[Long])
        responseBodySizeRef <- F.ref(Option.empty[Long])
        completionStartedRef <- F.ref(false)
        _ <- ops.increaseActiveRequests(metricsRequest, context)
        requestWithMetrics = request.withBodyStream(
          measureBodyBytes(request.body, request.contentLength, requestBodySizeRef)
        )
      } yield ContextRequest(
        MetricsEntry2(
          metricsRequest,
          startTime,
          context,
          requestBodySizeRef,
          responseBodySizeRef,
          completionStartedRef,
        ),
        requestWithMetrics,
      )

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
        responseHasNoBody =
          metrics.request.requestPrelude.method == Method.HEAD || !response.status.isEntityAllowed
        _ <- metrics.responseBodySizeRef.set(
          if (responseHasNoBody) Some(0L)
          else None
        )
        respWithMetrics =
          if (responseHasNoBody) response
          else
            response.withBodyStream(
              // Close body-owned cleanup before the bracket's weak completion callback on
              // normal compilation, including compilation through a Resource.
              measureBodyBytes(
                response.body,
                response.contentLength,
                metrics.responseBodySizeRef,
              ).scope
            )
      } yield ContextResponse(prelude, respWithMetrics)

    def recordCompletion(
        metrics: MetricsEntry2[F, ops.Context],
        response: Option[ResponsePrelude],
        terminationType: Option[TerminationType],
    ): F[Unit] =
      for {
        totalTime <- stopMetrics(metrics)
        _ <- ops.recordTotalTime(
          metrics.request,
          response,
          terminationType,
          totalTime,
          metrics.context,
        )
        requestBodySize <- metrics.requestBodySizeRef.get
        // For either body, no observed size means zero only without Content-Length;
        // a declared length alone does not prove the body was consumed.
        _ <- requestBodySize
          .orElse(
            metrics.request.requestPrelude.headers
              .get[`Content-Length`]
              .fold[Option[Long]](Some(0L))(_ => None)
          )
          .traverse_ { size =>
            ops.recordRequestBodySize(
              metrics.request,
              response,
              terminationType,
              size,
              metrics.context,
            )
          }
        _ <- response.fold(F.unit) { resp =>
          for {
            responseBodySize <- metrics.responseBodySizeRef.get
            _ <- responseBodySize
              .orElse(
                resp.headers
                  .get[`Content-Length`]
                  .fold[Option[Long]](Some(0L))(_ => None)
              )
              .traverse_ { size =>
                ops.recordResponseBodySize(
                  metrics.request,
                  resp,
                  terminationType,
                  size,
                  metrics.context,
                )
              }
          } yield ()
        }
      } yield ()

    def finishMetrics(
        metrics: MetricsEntry2[F, ops.Context],
        response: Option[ResponsePrelude],
        terminationType: Option[TerminationType],
    ): F[Unit] =
      // Both bracket release paths run with cancellation masked.
      metrics.completionStartedRef.getAndSet(true).flatMap {
        case true => F.unit
        case false => recordCompletion(metrics, response, terminationType)
      }

    Kleisli { request =>
      val metricsRequest = MetricsRequest.fromRequest(request)

      ops.createContext(metricsRequest).flatMap {
        case Some(context) =>
          BracketRequestResponse
            .bracketRequestResponseCaseApp_[F, MetricsEntry2[F, ops.Context], ResponsePrelude](
              req => startMetrics(req, metricsRequest, context)
            ) { case (metrics, response, outcome) =>
              val terminationType = outcome match {
                case Outcome.Succeeded(_) => None
                case Outcome.Errored(e) =>
                  Some(response.fold[TerminationType](Error(e))(_ => Abnormal(e)))
                case Outcome.Canceled() => Some(Canceled)
              }
              finishMetrics(metrics, response, terminationType)
            }(F)(Kleisli { case ContextRequest(metrics, requestWithMetrics) =>
              app(requestWithMetrics).flatMap(metricHeaders(metrics, _))
            })
            .run(request)

        case None =>
          app(request)
      }
    }
  }

}
