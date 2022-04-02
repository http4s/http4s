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
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType.Abnormal
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TerminationType.Error

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

  private[this] final case class MetricsRequestContext[Classifier](
      method: Method,
      startTime: Long,
      classifier: Option[Classifier],
  )

  /** A server middleware capable of recording metrics
    *
    * @param ops a algebra describing the metrics operations
    * @param emptyResponseHandler an optional http status to be registered for requests that do not match
    * @param errorResponseHandler a function that maps a [[java.lang.Throwable]] to an optional http status code to register
    * @param classifierF a function that allows to add a classifier that can be customized per request, with the type being
    *                    specified within the Classifier generic type
    * @return the metrics middleware
    */
  def apply[F[_], Classifier](
      ops: MetricsOps[F, Classifier],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => Option[Classifier] = { (_: Request[F]) =>
        None
      },
  )(routes: HttpRoutes[F])(implicit F: Clock[F], C: MonadCancel[F, Throwable]): HttpRoutes[F] =
    effect[F, Classifier](ops, emptyResponseHandler, errorResponseHandler, classifierF(_).pure[F])(
      routes
    )

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
    * @param classifierF a function that allows to add a classifier that can be customized per request, with the type being
    *                    specified within the Classifier generic type
    * @return the metrics middleware
    */
  def effect[F[_], Classifier](
      ops: MetricsOps[F, Classifier],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      classifierF: Request[F] => F[Option[Classifier]],
  )(routes: HttpRoutes[F])(implicit F: Clock[F], C: MonadCancel[F, Throwable]): HttpRoutes[F] =
    BracketRequestResponse
      .bracketRequestResponseCaseRoutes_[F, MetricsRequestContext[Classifier], Status] {
        (request: Request[F]) =>
          classifierF(request).flatMap { classifier =>
            ops.increaseActiveRequests(classifier) *>
              F.monotonic
                .map(startTime =>
                  ContextRequest(
                    MetricsRequestContext(request.method, startTime.toNanos, classifier),
                    request,
                  )
                )
          }
      } { case (context, maybeStatus, outcome) =>
        // Decrease active requests _first_ in case any of the other effects
        // trigger an error. This differs from the < 0.21.14 semantics, which
        // decreased it _after_ the other effects. This may have been the
        // reason the active requests counter was reported to have drifted.
        ops.decreaseActiveRequests(context.classifier) *>
          F.monotonic
            .map(endTime => endTime.toNanos - context.startTime)
            .flatMap(totalTime =>
              outcome match {
                case Outcome.Succeeded(_) =>
                  (maybeStatus <+> emptyResponseHandler).traverse_(status =>
                    ops.recordTotalTime(context.method, status, totalTime, context.classifier)
                  )
                case Outcome.Errored(e) =>
                  maybeStatus.fold {
                    // If an error occurred, and the status is empty, this means
                    // that an error occurred before the routes could generate a
                    // response.
                    ops.recordHeadersTime(context.method, totalTime, context.classifier) *>
                      ops.recordAbnormalTermination(totalTime, Error(e), context.classifier) *>
                      errorResponseHandler(e).traverse_(status =>
                        ops.recordTotalTime(context.method, status, totalTime, context.classifier)
                      )
                  }(status =>
                    // If an error occurred, but the status is non-empty, this
                    // means the error occurred during the stream processing of
                    // the response body. In this case recordHeadersTime would
                    // have been invoked in the normal manner so we do not need
                    // to invoke it here.
                    ops.recordAbnormalTermination(totalTime, Abnormal(e), context.classifier) *>
                      ops.recordTotalTime(context.method, status, totalTime, context.classifier)
                  )
                case Outcome.Canceled() =>
                  ops.recordAbnormalTermination(totalTime, Canceled, context.classifier)
              }
            )
      }(C)(
        Kleisli((contextRequest: ContextRequest[F, MetricsRequestContext[Classifier]]) =>
          routes
            .run(contextRequest.req)
            .semiflatMap(response =>
              F.monotonic
                .map(now => now.toNanos - contextRequest.context.startTime)
                .flatTap(headerTime =>
                  ops.recordHeadersTime(
                    contextRequest.context.method,
                    headerTime,
                    contextRequest.context.classifier,
                  )
                ) *> C.pure(ContextResponse(response.status, response))
            )
        )
      )

}
