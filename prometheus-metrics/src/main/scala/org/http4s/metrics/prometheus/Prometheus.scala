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

package org.http4s.metrics.prometheus

import cats.data.NonEmptyList
import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import io.prometheus.client._
import org.http4s.{Method, Status}
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.{Abnormal, Canceled, Error, Timeout}

/** [[MetricsOps]] algebra capable of recording Prometheus metrics
  *
  * For example, the following code would wrap a [[org.http4s.HttpRoutes]] with a [[org.http4s.server.middleware.Metrics]]
  * that records metrics to a given metric registry.
  * {{{
  * import cats.effect.{Resource, IO}
  * import org.http4s.server.middleware.Metrics
  * import org.http4s.metrics.Prometheus
  *
  * val meteredRoutes: Resource[IO, HttpRoutes[IO]] =
  *   Prometheus.metricsOps[IO](registry, "server").map(ops => Metrics[IO](ops)(testRoutes))
  *
  * val settings: PrometheusMetricsSettings = PrometheusMetricsSettings.DefaultSettings
  *
  * val anotherMeteredRoutes: Resource[IO, HttpRoutes[IO]] =
  *   Prometheus.metricsOps[IO](registry, settings).map(ops => Metrics[IO](ops)(testRoutes))
  * }}}
  *
  * Analogously, the following code would wrap a [[org.http4s.client.Client]] with a [[org.http4s.client.middleware.Metrics]]
  * that records metrics to a given metric registry, classifying the metrics by HTTP method.
  * {{{
  * import cats.effect.{Resource, IO}
  * import org.http4s.client.middleware.Metrics
  * import org.http4s.metrics.Prometheus
  *
  * val classifierFunc = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
  *
  * val meteredClient: Resource[IO, Client[IO]] =
  *   Prometheus.metricsOps[IO](registry, "client").map(ops => Metrics[IO](ops, classifierFunc)(client))
  *
  * val settings: PrometheusMetricsSettings = PrometheusMetricsSettings.DefaultSettings
  *
  * val anotherMeteredClient: Resource[IO, Client[IO]] =
  *   Prometheus.metricsOps[IO](registry, settings).map(ops => Metrics[IO](ops, classifierFunc)(client))
  * }}}
  *
  * Registers the following metrics with default names:
  *
  * {prefix}_response_duration_seconds{labels=classifier,method,phase} - Histogram
  *
  * {prefix}_active_request_count{labels=classifier} - Gauge
  *
  * {prefix}_request_count{labels=classifier,method,status} - Counter
  *
  * {prefix}_abnormal_terminations{labels=classifier,termination_type} - Histogram
  *
  * Also, you can specify a metrics suffix too with a [[org.http4s.metrics.prometheus.PrometheusMetricsNames]]:
  *
  * {prefix}_{suffix}{labels=classifier,method,phase} - Histogram
  *
  * Or you can specify fully custom names of the metrics with a [[org.http4s.metrics.prometheus.PrometheusMetricsNames]]:
  *
  * Labels --
  *
  * method: Enumeration
  * values: get, put, post, head, move, options, trace, connect, delete, other
  *
  * phase: Enumeration
  * values: headers, body
  *
  * code: Enumeration
  * values:  1xx, 2xx, 3xx, 4xx, 5xx
  *
  * termination_type: Enumeration
  * values: abnormal, error, timeout
  */
object Prometheus {
  def collectorRegistry[F[_]](implicit F: Sync[F]): Resource[F, CollectorRegistry] =
    Resource.make(F.delay(new CollectorRegistry()))(cr => F.delay(cr.clear()))

  /** Creates a [[MetricsOps]] that supports Prometheus metrics
    *
    * @param registry a metrics collector registry
    * @param prefix a prefix that will be added to all metrics
    * @param metricsNames names of the metrics that will be used as a suffix for the metrics
    */
  def metricsOps[F[_]: Sync](
      registry: CollectorRegistry,
      prefix: String = "org_http4s_server",
      responseDurationSecondsHistogramBuckets: NonEmptyList[Double] =
        PrometheusMetricsSettings.DefaultHistogramBuckets,
      metricsNames: PrometheusMetricsNames = PrometheusMetricsNames.DefaultMetricsNames
  ): Resource[F, MetricsOps[F]] =
    for {
      metrics <- createMetricsCollection(
        registry = registry,
        prefix = Option(prefix),
        responseDurationSecondsHistogramBuckets = responseDurationSecondsHistogramBuckets,
        metricsNames = metricsNames
      )
    } yield createMetricsOps(metrics)

  /** Creates a [[MetricsOps]] that supports Prometheus metrics with custom settings
    *
    * @param registry a metrics collector registry
    * @param settings a Prometheus metrics settings
    */
  def metricsOps[F[_]: Sync](
      registry: CollectorRegistry,
      settings: PrometheusMetricsSettings
  ): Resource[F, MetricsOps[F]] =
    for {
      metrics <- createMetricsCollection(
        registry = registry,
        prefix = Option.empty[String],
        responseDurationSecondsHistogramBuckets =
          settings.responseDurationSecondsHistogramBuckets.toNonEmptyList,
        metricsNames = settings.metricsNames
      )
    } yield createMetricsOps(metrics)

  private def createMetricsOps[F[_]](
      metrics: MetricsCollection
  )(implicit F: Sync[F]): MetricsOps[F] =
    new MetricsOps[F] {
      override def increaseActiveRequests(classifier: Option[String]): F[Unit] =
        F.delay {
          metrics.activeRequests
            .labels(label(classifier))
            .inc()
        }

      override def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
        F.delay {
          metrics.activeRequests
            .labels(label(classifier))
            .dec()
        }

      override def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String]
      ): F[Unit] =
        F.delay {
          metrics.responseDuration
            .labels(label(classifier), reportMethod(method), Phase.report(Phase.Headers))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
        }

      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]
      ): F[Unit] =
        F.delay {
          metrics.responseDuration
            .labels(label(classifier), reportMethod(method), Phase.report(Phase.Body))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
          metrics.requests
            .labels(label(classifier), reportMethod(method), reportStatus(status))
            .inc()
        }

      override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String]): F[Unit] =
        terminationType match {
          case Abnormal(e) => recordAbnormal(elapsed, classifier, e)
          case Error(e) => recordError(elapsed, classifier, e)
          case Canceled => recordCanceled(elapsed, classifier)
          case Timeout => recordTimeout(elapsed, classifier)
        }

      private def recordCanceled(elapsed: Long, classifier: Option[String]): F[Unit] =
        F.delay {
          metrics.abnormalTerminations
            .labels(
              label(classifier),
              AbnormalTermination.report(AbnormalTermination.Canceled),
              label(Option.empty))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
        }

      private def recordAbnormal(
          elapsed: Long,
          classifier: Option[String],
          cause: Throwable): F[Unit] =
        F.delay {
          metrics.abnormalTerminations
            .labels(
              label(classifier),
              AbnormalTermination.report(AbnormalTermination.Abnormal),
              label(Option(cause.getClass.getName)))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
        }

      private def recordError(
          elapsed: Long,
          classifier: Option[String],
          cause: Throwable): F[Unit] =
        F.delay {
          metrics.abnormalTerminations
            .labels(
              label(classifier),
              AbnormalTermination.report(AbnormalTermination.Error),
              label(Option(cause.getClass.getName)))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
        }

      private def recordTimeout(elapsed: Long, classifier: Option[String]): F[Unit] =
        F.delay {
          metrics.abnormalTerminations
            .labels(
              label(classifier),
              AbnormalTermination.report(AbnormalTermination.Timeout),
              label(Option.empty))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
        }

      private def label(value: Option[String]): String = value.getOrElse("")

      private def reportStatus(status: Status): String =
        status.code match {
          case hundreds if hundreds < 200 => "1xx"
          case twohundreds if twohundreds < 300 => "2xx"
          case threehundreds if threehundreds < 400 => "3xx"
          case fourhundreds if fourhundreds < 500 => "4xx"
          case _ => "5xx"
        }

      private def reportMethod(m: Method): String =
        m match {
          case Method.GET => "get"
          case Method.PUT => "put"
          case Method.POST => "post"
          case Method.HEAD => "head"
          case Method.MOVE => "move"
          case Method.OPTIONS => "options"
          case Method.TRACE => "trace"
          case Method.CONNECT => "connect"
          case Method.DELETE => "delete"
          case _ => "other"
        }
    }

  private def createMetricsCollection[F[_]: Sync](
      registry: CollectorRegistry,
      prefix: Option[String],
      responseDurationSecondsHistogramBuckets: NonEmptyList[Double],
      metricsNames: PrometheusMetricsNames
  ): Resource[F, MetricsCollection] = {
    val metricsNamesWithPrefix = prefix match {
      case None =>
        Resource.pure[F, PrometheusMetricsNames](metricsNames)

      case Some(prefix) =>
        Resource.eval(Sync[F].fromEither(metricsNames.withPrefix(prefix)))
    }

    val responseDuration: Resource[F, Histogram] =
      metricsNamesWithPrefix.map(_.responseDuration) >>= (responseDuration =>
        registerCollector(
          Histogram
            .build()
            .buckets(responseDurationSecondsHistogramBuckets.toList: _*)
            .name(responseDuration)
            .help("Response Duration in seconds.")
            .labelNames("classifier", "method", "phase")
            .create(),
          registry
        ))

    val activeRequests: Resource[F, Gauge] =
      metricsNamesWithPrefix.map(_.activeRequests) >>= (activeRequests =>
        registerCollector(
          Gauge
            .build()
            .name(activeRequests)
            .help("Total Active Requests.")
            .labelNames("classifier")
            .create(),
          registry
        ))

    val requests: Resource[F, Counter] =
      metricsNamesWithPrefix.map(_.requests) >>= (requests =>
        registerCollector(
          Counter
            .build()
            .name(requests)
            .help("Total Requests.")
            .labelNames("classifier", "method", "status")
            .create(),
          registry
        ))

    val abnormalTerminations: Resource[F, Histogram] =
      metricsNamesWithPrefix.map(_.abnormalTerminations) >>= (abnormalTerminations =>
        registerCollector(
          Histogram
            .build()
            .name(abnormalTerminations)
            .help("Total Abnormal Terminations.")
            .labelNames("classifier", "termination_type", "cause")
            .create(),
          registry
        ))

    (responseDuration, activeRequests, requests, abnormalTerminations).mapN(MetricsCollection.apply)
  }

  private[prometheus] def registerCollector[F[_], C <: Collector](
      collector: C,
      registry: CollectorRegistry
  )(implicit F: Sync[F]): Resource[F, C] =
    Resource.make(F.delay(collector.register[C](registry)))(c => F.delay(registry.unregister(c)))
}

final case class MetricsCollection(
    responseDuration: Histogram,
    activeRequests: Gauge,
    requests: Counter,
    abnormalTerminations: Histogram
)

private sealed trait Phase
private object Phase {
  case object Headers extends Phase
  case object Body extends Phase
  def report(s: Phase): String =
    s match {
      case Headers => "headers"
      case Body => "body"
    }
}

private sealed trait AbnormalTermination
private object AbnormalTermination {
  case object Abnormal extends AbnormalTermination
  case object Error extends AbnormalTermination
  case object Timeout extends AbnormalTermination
  case object Canceled extends AbnormalTermination
  def report(t: AbnormalTermination): String =
    t match {
      case Abnormal => "abnormal"
      case Timeout => "timeout"
      case Error => "error"
      case Canceled => "cancel"
    }
}
