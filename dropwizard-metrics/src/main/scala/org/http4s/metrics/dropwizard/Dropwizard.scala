/*
 * Copyright 2018 http4s.org
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

package org.http4s.metrics.dropwizard

import cats.effect.Sync
import com.codahale.metrics.MetricRegistry
import java.util.concurrent.TimeUnit

import org.http4s.{Method, Status}
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.{Abnormal, Canceled, Error, Timeout}

/** [[MetricsOps]] algebra capable of recording Dropwizard metrics
  *
  * For example, the following code would wrap a [[org.http4s.HttpRoutes]] with a [[org.http4s.server.middleware.Metrics]]
  * that records metrics to a given metric registry.
  * {{{
  * import org.http4s.client.middleware.Metrics
  * import org.http4s.client.dropwizard.Dropwizard
  *
  * val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(testRoutes)
  * }}}
  *
  * Analogously, the following code would wrap a [[org.http4s.client.Client]] with a [[org.http4s.client.middleware.Metrics]]
  * that records metrics to a given Metric Registry, classifying the metrics by HTTP method.
  * {{{
  * import org.http4s.client.metrics.core.Metrics
  * import org.http4s.client.metrics.dropwizard.Dropwizard
  *
  * val classifierFunc = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
  * val meteredClient = Metrics(Dropwizard(registry, "client"), classifierFunc)(client)
  * }}}
  *
  * Registers the following metrics:
  *
  * {prefix}.{classifier}.active.requests - Counter
  * {prefix}.{classifier}.requests.headers - Timer
  * {prefix}.{classifier}.requests.total - Timer
  * {prefix}.{classifier}.get-requests - Timer
  * {prefix}.{classifier}.post-requests - Timer
  * {prefix}.{classifier}.put-requests - Timer
  * {prefix}.{classifier}.head-requests - Timer
  * {prefix}.{classifier}.move-requests - Timer
  * {prefix}.{classifier}.options-requests - Timer
  * {prefix}.{classifier}.trace-requests - Timer
  * {prefix}.{classifier}.connect-requests - Timer
  * {prefix}.{classifier}.delete-requests - Timer
  * {prefix}.{classifier}.other-requests - Timer
  * {prefix}.{classifier}.1xx-responses - Timer
  * {prefix}.{classifier}.2xx-responses - Timer
  * {prefix}.{classifier}.3xx-responses - Timer
  * {prefix}.{classifier}.4xx-responses - Timer
  * {prefix}.{classifier}.5xx-responses - Timer
  * {prefix}.{classifier}.errors - Timer
  * {prefix}.{classifier}.timeouts - Timer
  * {prefix}.{classifier}.abnormal-terminations - Timer
  */
object Dropwizard {

  /** Creates a [[MetricsOps]] that supports Dropwizard metrics
    *
    * @param registry a dropwizard metric registry
    * @param prefix a prefix that will be added to all metrics
    */
  def apply[F[_]](registry: MetricRegistry, prefix: String = "org.http4s.server")(implicit
      F: Sync[F]): MetricsOps[F] =
    new MetricsOps[F] {
      override def increaseActiveRequests(classifier: Option[String]): F[Unit] =
        F.delay {
          registry.counter(s"${namespace(prefix, classifier)}.active-requests").inc()
        }

      override def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
        F.delay {
          registry.counter(s"${namespace(prefix, classifier)}.active-requests").dec()
        }

      override def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String]): F[Unit] =
        F.delay {
          registry
            .timer(s"${namespace(prefix, classifier)}.requests.headers")
            .update(elapsed, TimeUnit.NANOSECONDS)
        }

      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]): F[Unit] =
        F.delay {
          registry
            .timer(s"${namespace(prefix, classifier)}.requests.total")
            .update(elapsed, TimeUnit.NANOSECONDS)
          registry
            .timer(s"${namespace(prefix, classifier)}.${requestTimer(method)}")
            .update(elapsed, TimeUnit.NANOSECONDS)
          registerStatusCode(status, elapsed, classifier)
        }

      override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String]): F[Unit] =
        terminationType match {
          case Abnormal(_) => recordAbnormal(elapsed, classifier)
          case Error(_) => recordError(elapsed, classifier)
          case Canceled => recordCanceled(elapsed, classifier)
          case Timeout => recordTimeout(elapsed, classifier)
        }

      private def recordCanceled(elapsed: Long, classifier: Option[String]): F[Unit] =
        F.delay {
          registry
            .timer(s"${namespace(prefix, classifier)}.canceled")
            .update(elapsed, TimeUnit.NANOSECONDS)
        }

      private def recordAbnormal(elapsed: Long, classifier: Option[String]): F[Unit] =
        F.delay {
          registry
            .timer(s"${namespace(prefix, classifier)}.abnormal-terminations")
            .update(elapsed, TimeUnit.NANOSECONDS)
        }

      private def recordError(elapsed: Long, classifier: Option[String]): F[Unit] =
        F.delay {
          registry
            .timer(s"${namespace(prefix, classifier)}.errors")
            .update(elapsed, TimeUnit.NANOSECONDS)
        }

      private def recordTimeout(elapsed: Long, classifier: Option[String]): F[Unit] =
        F.delay {
          registry
            .timer(s"${namespace(prefix, classifier)}.timeouts")
            .update(elapsed, TimeUnit.NANOSECONDS)
        }

      private def namespace(prefix: String, classifier: Option[String]): String =
        classifier.map(d => s"${prefix}.${d}").getOrElse(s"${prefix}.default")

      private def registerStatusCode(status: Status, elapsed: Long, classifier: Option[String]) =
        (status.code match {
          case hundreds if hundreds < 200 =>
            registry.timer(s"${namespace(prefix, classifier)}.1xx-responses")
          case twohundreds if twohundreds < 300 =>
            registry.timer(s"${namespace(prefix, classifier)}.2xx-responses")
          case threehundreds if threehundreds < 400 =>
            registry.timer(s"${namespace(prefix, classifier)}.3xx-responses")
          case fourhundreds if fourhundreds < 500 =>
            registry.timer(s"${namespace(prefix, classifier)}.4xx-responses")
          case _ => registry.timer(s"${namespace(prefix, classifier)}.5xx-responses")
        }).update(elapsed, TimeUnit.NANOSECONDS)

      private def requestTimer(method: Method): String =
        method match {
          case Method.GET => "get-requests"
          case Method.POST => "post-requests"
          case Method.PUT => "put-requests"
          case Method.HEAD => "head-requests"
          case Method.MOVE => "move-requests"
          case Method.OPTIONS => "options-requests"
          case Method.TRACE => "trace-requests"
          case Method.CONNECT => "connect-requests"
          case Method.DELETE => "delete-requests"
          case _ => "other-requests"
        }
    }
}
