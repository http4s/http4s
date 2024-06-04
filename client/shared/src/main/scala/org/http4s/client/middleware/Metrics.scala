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

package org.http4s.client.middleware

import cats.effect.Clock
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all.*
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.metrics.{CustomMetricsOps, MetricsOps}
import org.http4s.metrics.TerminationType.Error
import org.http4s.metrics.TerminationType.Timeout
import org.http4s.util.{SizedSeq, SizedSeq0}

import scala.concurrent.TimeoutException

/** Client middleware to record metrics for the http4s client.
  *
  * This middleware will record:
  * - Number of active requests
  * - Time duration to receive the response headers
  * - Time duration to process the whole response body
  * - Time duration of errors, timeouts and other abnormal terminations
  *
  * This middleware can be extended to support any metrics ecosystem by
  * implementing the [[org.http4s.metrics.MetricsOps]] type
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
      },
  )(client: Client[F])(implicit F: Clock[F], C: Concurrent[F]): Client[F] =
    effect(ops, classifierF.andThen(_.pure[F]))(client)

  def withCustomLabels[F[_], SL <: SizedSeq[String]](
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      classifierF: Request[F] => Option[String] = { (_: Request[F]) =>
        None
      },
  )(client: Client[F])(implicit F: Clock[F], C: Concurrent[F]): Client[F] =
    effectWithCustomLabels(ops, customLabelValues, classifierF.andThen(_.pure[F]))(client)

  /** Wraps a [[Client]] with a middleware capable of recording metrics
    *
    * Same as [[apply]], but can classify requests effectually, e.g. performing side-effects or examining the body.
    * Failed attempt to classify the request (e.g. failing with `F.raiseError`) leads to not recording metrics for that request.
    *
    * @note Compiling the request body in `classifierF` is unsafe, unless you are using some caching middleware.
    *
    * @param ops a algebra describing the metrics operations
    * @param classifierF a function that allows to add a classifier that can be customized per request
    * @param client the [[Client]] to gather metrics from
    * @return the metrics middleware wrapping the [[Client]]
    */
  def effect[F[_]](ops: MetricsOps[F], classifierF: Request[F] => F[Option[String]])(
      client: Client[F]
  )(implicit F: Clock[F], C: Concurrent[F]): Client[F] = {
    val cops = CustomMetricsOps.fromMetricsOps(ops)
    val emptyCustomLabelValues = SizedSeq0[String]()
    Client(withMetrics(client, cops, emptyCustomLabelValues, classifierF))
  }

  def effectWithCustomLabels[F[_], SL <: SizedSeq[String]](
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      classifierF: Request[F] => F[Option[String]],
  )(client: Client[F])(implicit F: Clock[F], C: Concurrent[F]): Client[F] =
    Client(withMetrics(client, ops, customLabelValues, classifierF))

  private def withMetrics[F[_], SL <: SizedSeq[String]](
      client: Client[F],
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      classifierF: Request[F] => F[Option[String]],
  )(req: Request[F])(implicit F: Clock[F], C: Concurrent[F]): Resource[F, Response[F]] =
    for {
      statusRef <- Resource.eval(C.ref[Option[Status]](None))
      start <- Resource.eval(F.monotonic)
      resp <- executeRequestAndRecordMetrics(
        client,
        ops,
        customLabelValues,
        classifierF,
        req,
        statusRef,
        start.toNanos,
      )
    } yield resp

  private def executeRequestAndRecordMetrics[F[_], SL <: SizedSeq[String]](
      client: Client[F],
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      classifierF: Request[F] => F[Option[String]],
      req: Request[F],
      statusRef: Ref[F, Option[Status]],
      start: Long,
  )(implicit F: Clock[F], C: Concurrent[F]): Resource[F, Response[F]] =
    (for {
      classifier <- Resource.eval(classifierF(req))
      _ <- Resource.make(ops.increaseActiveRequests(classifier, customLabelValues))(_ =>
        ops.decreaseActiveRequests(classifier, customLabelValues)
      )
      _ <- Resource.onFinalize(
        F.monotonic
          .flatMap(now =>
            statusRef.get.flatMap(oStatus =>
              oStatus.traverse_(status =>
                ops.recordTotalTime(
                  req.method,
                  status,
                  now.toNanos - start,
                  classifier,
                  customLabelValues,
                )
              )
            )
          )
      )
      resp <- client.run(req)
      _ <- Resource.eval(statusRef.set(Some(resp.status)))
      end <- Resource.eval(F.monotonic)
      _ <- Resource.eval(
        ops.recordHeadersTime(req.method, end.toNanos - start, classifier, customLabelValues)
      )
    } yield resp).handleErrorWith { (e: Throwable) =>
      Resource.eval(
        classifierF(req).flatMap(registerError(start, ops, customLabelValues, _)(e)) *>
          C.raiseError[Response[F]](e)
      )
    }

  private def registerError[F[_], SL <: SizedSeq[String]](
      start: Long,
      ops: CustomMetricsOps[F, SL],
      customLabelValues: SL,
      classifier: Option[String],
  )(e: Throwable)(implicit F: Clock[F], C: Concurrent[F]): F[Unit] =
    F.monotonic
      .flatMap { now =>
        if (e.isInstanceOf[TimeoutException])
          ops.recordAbnormalTermination(now.toNanos - start, Timeout, classifier, customLabelValues)
        else
          ops.recordAbnormalTermination(now.toNanos - start, Error(e), classifier, customLabelValues)
      }
}
