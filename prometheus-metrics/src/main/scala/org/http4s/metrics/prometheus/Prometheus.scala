package org.http4s.metrics.prometheus

import cats.effect.Sync
import io.prometheus.client._
import org.http4s.{Method, Status}
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.{Abnormal, Error, Timeout}

/**
  * [[MetricsOps]] algebra capable of recording Prometheus metrics
  *
  * For example to following code would wrap a [[org.http4s.client.Client]] with a [[org.http4s.client.metrics.core.Metrics]]
  * that records metrics to a given Metric Registry, classifying the metrics by HTTP method.
  * {{{
  * import org.http4s.client.metrics.core.Metrics
  * import org.http4s.client.metrics.prometheus.Prometheus
  *
  * requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
  * val meteredClient = Metrics(Prometheus(registry, "client"), requestMethodClassifier)(client)
  * }}}
  */
object Prometheus {

  /**
    * Creates a  [[MetricsOps]] that supports Prometheus metrics
    * *
    * * @param registry a metrics collector registry
    * * @param prefix a prefix that will be added to all metrics
    **/
  def apply[F[_]](registry: CollectorRegistry, prefix: String)(implicit F: Sync[F]): MetricsOps[F] =
    new MetricsOps[F] {

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
          method: Method,
          elapsed: Long,
          classifier: Option[String]): F[Unit] = F.delay {
        metrics.responseDuration
          .labels(
            label(classifier),
            reportMethod(method),
            ResponsePhase.report(ResponsePhase.ResponseReceived))
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))

      }

      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]): F[Unit] =
        F.delay {
          metrics.responseDuration
            .labels(
              label(classifier),
              reportMethod(method),
              ResponsePhase.report(ResponsePhase.BodyProcessed))
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
          metrics.responseCounter
            .labels(label(classifier), reportMethod(method), reportStatus(status))
            .inc()
        }

      override def recordAbnormalTermination(elapsed: Long, terminationType: TerminationType, classifier: Option[String]): F[Unit] = terminationType match {
        case Abnormal => F.unit
        case Error    => recordError(elapsed, classifier)
        case Timeout  => recordTimeout(elapsed, classifier)
      }

      private def recordError(elapsed: Long, classifier: Option[String]): F[Unit] = F.delay {
        metrics.abnormalTerminations
          .labels(label(classifier), AbnormalTermination.report(AbnormalTermination.Error))
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
      }

      private def recordTimeout(elapsed: Long, classifier: Option[String]): F[Unit] = F.delay {
        metrics.abnormalTerminations
          .labels(label(classifier), AbnormalTermination.report(AbnormalTermination.Timeout))
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
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

      private def reportMethod(m: Method): String = m match {
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

      val metrics =
        ClientMetrics(
          responseDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames("classifier", "method", "response_phase")
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
            .labelNames("classifier", "method", "status")
            .register(registry),
          abnormalTerminations = Histogram
            .build()
            .name(prefix + "_" + "abnormal_terminations")
            .help("Total Abnormal Terminations.")
            .labelNames("classifier", "termination_type")
            .register(registry)
        )
    }
}

case class ClientMetrics(
    responseDuration: Histogram,
    activeRequests: Gauge,
    responseCounter: Counter,
    abnormalTerminations: Histogram
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

private sealed trait AbnormalTermination
private object AbnormalTermination {
  case object Error extends AbnormalTermination
  case object Timeout extends AbnormalTermination
  def report(t: AbnormalTermination): String = t match {
    case Timeout => "timeout"
    case Error => "error"
  }
}
