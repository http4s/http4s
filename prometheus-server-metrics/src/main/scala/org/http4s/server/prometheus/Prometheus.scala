package org.http4s.server.prometheus

import cats.effect.Sync
import io.prometheus.client._
import org.http4s.{Method, Status}
import org.http4s.server.middleware.{TerminationType, MetricsOps}
import org.http4s.server.middleware.TerminationType.{Abnormal, Error, Timeout}

/**
  * [[MetricsOps]] algebra capable of recording Prometheus metrics
  *
  * TODO
  *
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
        metrics.requestDuration // responseDuration
          .labels(
            label(classifier),
            reportMethod(method),
            ServingPhase.report(ServingPhase.HeaderPhase) // ResponsePhase.report(ResponsePhase.ResponseReceived))
          )
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
      }

      // FIXME: Status not being used
      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]): F[Unit] =
        F.delay {
          metrics.requestDuration // responseDuration
            .labels(
              label(classifier),
              reportMethod(method),
              ServingPhase.report(ServingPhase.BodyPhase)  // ResponsePhase.report(ResponsePhase.BodyProcessed))
            )
            .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
          metrics.requestCounter // responseCounter
            .labels(label(classifier), reportMethod(method), reportStatus(status))
            .inc()
        }

      override def recordAbnormalTermination(elapsed: Long, terminationType: TerminationType, classifier: Option[String]): F[Unit] = terminationType match {
        case Abnormal => recordAbnormal(elapsed, classifier)
        case Error    => recordError(elapsed, classifier)
        case Timeout  => F.unit
      }

      private  def recordAbnormal(elapsed: Long, classifier: Option[String]): F[Unit] = F.delay {
        metrics.abnormalTerminations
          .labels(label(classifier), AbnormalTermination.report(AbnormalTermination.Abnormal))
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
      }

      private def recordError(elapsed: Long, classifier: Option[String]): F[Unit] = F.delay {
        metrics.abnormalTerminations
          .labels(label(classifier), AbnormalTermination.report(AbnormalTermination.ServerError))
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
        ServiceMetrics(
          requestDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames("classifier", "method", "serving_phase")
            .register(registry),
          activeRequests = Gauge
            .build()
            .name(prefix + "_" + "active_request_count")
            .help("Total Active Requests.")
            .labelNames("classifier")
            .register(registry),
          requestCounter = Counter
            .build()
            .name(prefix + "_" + "response_total")
            .help("Total Responses.")
            .labelNames("classifier", "method", "code")
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

private final case class ServiceMetrics(
  requestDuration: Histogram,
  activeRequests: Gauge,
  requestCounter: Counter,
  abnormalTerminations: Histogram
)


private sealed trait ServingPhase
private object ServingPhase {
  case object HeaderPhase extends ServingPhase
  case object BodyPhase extends ServingPhase
  def report(s: ServingPhase): String = s match {
    case HeaderPhase => "header_phase"
    case BodyPhase => "body_phase"
  }
}

private sealed trait AbnormalTermination
private object AbnormalTermination {
  case object Abnormal extends AbnormalTermination
  case object ServerError extends AbnormalTermination
  def report(t: AbnormalTermination): String = t match {
    case Abnormal => "abnormal_termination"
    case ServerError => "server_error"
  }
}


