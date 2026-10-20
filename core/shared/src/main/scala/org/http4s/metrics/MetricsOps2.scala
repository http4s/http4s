/*
 * Copyright 2013 http4s.org
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

package org.http4s.metrics

import cats.~>
import org.http4s.ResponsePrelude

import scala.concurrent.duration.FiniteDuration

/** Describes an algebra capable of writing metrics to a metrics registry.
  *
  * Unlike [[MetricsOps]], this algebra provides enough information to fill out all required and
  * optional [[https://opentelemetry.io/docs/specs/semconv/http/http-metrics OpenTelemetry attributes]].
  *
  * Client and server middleware record request and response body sizes as follows:
  *  - Without `Content-Length`, count the bytes in each chunk read from the body. Count the whole
  *    chunk even if the caller uses only part of it, such as with `take(1)`. Record zero if the
  *    body was not read, and the count so far if reading stops early, fails, or is canceled.
  *  - With `Content-Length`, use the header value without checking the actual byte count. Record
  *    it only after the stream ends and its finalizers succeed. Otherwise, skip the size metric,
  *    even if the header says zero. `take(n)` can read the last byte without running the stream
  *    to its end.
  *  - Record zero for HEAD responses and statuses that cannot have a body, regardless of
  *    `Content-Length`. Do not record a response size if there is no response.
  *
  * Read each body once. The client records these metrics when the response resource is released.
  * The server records them when the response body finishes. If a server response body is never
  * run, its completion metrics are never recorded.
  *
  * Middleware order determines whether sizes include compressed or decompressed bytes. A socket
  * write can fail after bytes have been counted, so these sizes can differ from bytes sent.
  */
trait MetricsOps2[F[_]] { self =>

  /** Backend-specific context shared by all metrics recorded for a request. */
  type Context

  /** Optionally creates the metrics context once at the beginning of a request.
    * Each metric provider can use its own context: otel4s can use Attributes, while Prometheus can
    * use Labels. Returning `None` excludes the request from all metrics and body instrumentation.
    */
  def createContext(request: MetricsRequest): F[Option[Context]]

  def increaseActiveRequests(request: MetricsRequest, context: Context): F[Unit]

  def decreaseActiveRequests(request: MetricsRequest, context: Context): F[Unit]

  def recordHeadersTime(
      request: MetricsRequest,
      elapsed: FiniteDuration,
      context: Context,
  ): F[Unit]

  def recordTotalTime(
      request: MetricsRequest,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      context: Context,
  ): F[Unit]

  def recordRequestBodySize(
      request: MetricsRequest,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Context,
  ): F[Unit]

  def recordResponseBodySize(
      request: MetricsRequest,
      response: ResponsePrelude,
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Context,
  ): F[Unit]

  def mapK[G[_]](fk: F ~> G): MetricsOps2[G] { type Context = self.Context } =
    new MetricsOps2[G] {
      override type Context = self.Context

      override def createContext(request: MetricsRequest): G[Option[Context]] =
        fk(self.createContext(request))

      override def increaseActiveRequests(
          request: MetricsRequest,
          context: Context,
      ): G[Unit] = fk(self.increaseActiveRequests(request, context))

      override def decreaseActiveRequests(
          request: MetricsRequest,
          context: Context,
      ): G[Unit] = fk(self.decreaseActiveRequests(request, context))

      override def recordHeadersTime(
          request: MetricsRequest,
          elapsed: FiniteDuration,
          context: Context,
      ): G[Unit] = fk(self.recordHeadersTime(request, elapsed, context))

      override def recordTotalTime(
          request: MetricsRequest,
          response: Option[ResponsePrelude],
          terminationType: Option[TerminationType],
          elapsed: FiniteDuration,
          context: Context,
      ): G[Unit] = fk(self.recordTotalTime(request, response, terminationType, elapsed, context))

      override def recordRequestBodySize(
          request: MetricsRequest,
          response: Option[ResponsePrelude],
          terminationType: Option[TerminationType],
          bodySizeBytes: Long,
          context: Context,
      ): G[Unit] = fk(
        self.recordRequestBodySize(request, response, terminationType, bodySizeBytes, context)
      )

      override def recordResponseBodySize(
          request: MetricsRequest,
          response: ResponsePrelude,
          terminationType: Option[TerminationType],
          bodySizeBytes: Long,
          context: Context,
      ): G[Unit] = fk(
        self.recordResponseBodySize(request, response, terminationType, bodySizeBytes, context)
      )
    }
}
