package org.http4s.client.metrics.prometheus

import cats.effect.Sync
import io.prometheus.client._
import org.http4s.Status
import org.http4s.client.metrics.core.{MetricsOps, MetricsOpsFactory}

/**
  * Implementation of [[MetricsOps]] that supports Prometheus metrics
  *
  * @param registry a metrics collector registry
  * @param prefix a prefix that will be added to all metrics
  */
class PrometheusOps[F[_]](registry: CollectorRegistry, prefix: String)(implicit F: Sync[F])
    extends MetricsOps[F] {

  override def increaseActiveRequests(classifier: Option[String]): F[Unit] = F.delay {
    metrics.activeRequests
      .labels(label(classifier))
      .inc()
  }

  override def decreaseActiveRequests(classifier: Option[String]): F[Unit] = F.delay {
    metrics.activeRequests
      .labels(label(classifier))
      .dec()
  }

  override def recordHeadersTime(
      status: Status,
      elapsed: Long,
      classifier: Option[String]): F[Unit] = F.delay {
    metrics.responseDuration
      .labels(
        label(classifier),
        reportStatus(status),
        ResponsePhase.report(ResponsePhase.ResponseReceived))
      .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
    metrics.responseCounter
      .labels(label(classifier), reportStatus(status))
      .inc()
  }

  override def recordTotalTime(status: Status, elapsed: Long, classifier: Option[String]): F[Unit] =
    F.delay {
      metrics.responseDuration
        .labels(
          label(classifier),
          reportStatus(status),
          ResponsePhase.report(ResponsePhase.BodyProcessed))
        .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
    }

  override def increaseErrors(classifier: Option[String]): F[Unit] = F.delay {
    metrics.clientErrorsCounter
      .labels(label(classifier))
      .inc()
  }

  override def increaseTimeouts(classifier: Option[String]): F[Unit] = F.delay {
    metrics.timeoutsCounter
      .labels(label(classifier))
      .inc()
  }

  private def label(classifier: Option[String]): String = classifier.getOrElse("")

  private def reportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

  val metrics =
    ClientMetrics(
      responseDuration = Histogram
        .build()
        .name(prefix + "_" + "response_duration_seconds")
        .help("Response Duration in seconds.")
        .labelNames("classifier", "code", "response_phase")
        .register(registry),
      activeRequests = Gauge
        .build()
        .name(prefix + "_" + "active_request_count")
        .help("Total Active Requests.")
        .labelNames("classifier")
        .register(registry),
      responseCounter = Counter
        .build()
        .name(prefix + "_" + "response_total")
        .help("Total Responses.")
        .labelNames("classifier", "code")
        .register(registry),
      clientErrorsCounter = Counter
        .build()
        .name(prefix + "_" + "client_errors_total")
        .help("Total Client Errors.")
        .labelNames("classifier")
        .register(registry),
      timeoutsCounter = Counter
        .build()
        .name(prefix + "_" + "client_timeouts_total")
        .help("Total Client Timeouts.")
        .labelNames("classifier")
        .register(registry)
    )
}

case class ClientMetrics(
    responseDuration: Histogram,
    activeRequests: Gauge,
    responseCounter: Counter,
    clientErrorsCounter: Counter,
    timeoutsCounter: Counter
)

private sealed trait ResponsePhase
private object ResponsePhase {
  case object ResponseReceived extends ResponsePhase
  case object BodyProcessed extends ResponsePhase
  def report(s: ResponsePhase): String = s match {
    case ResponseReceived => "response_received"
    case BodyProcessed => "body_processed"
  }
}

class PrometheusOpsFactory extends MetricsOpsFactory[CollectorRegistry] {

  override def instance[F[_]: Sync](registry: CollectorRegistry, prefix: String): MetricsOps[F] =
    new PrometheusOps[F](registry, prefix)
}

trait PrometheusOpsFactoryInstances {

  implicit val prometheusOpsFactory: MetricsOpsFactory[CollectorRegistry] =
    new PrometheusOpsFactory()
}
