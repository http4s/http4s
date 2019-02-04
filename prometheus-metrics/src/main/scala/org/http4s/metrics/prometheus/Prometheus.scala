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
  * For example, the following code would wrap a [[org.http4s.HttpRoutes]] with a [[org.http4s.server.middleware.Metrics]]
  * that records metrics to a given metric registry.
  * {{{
  * import org.http4s.client.middleware.Metrics
  * import org.http4s.client.prometheus.Prometheus
  *
  * val meteredRoutes = Metrics[IO](Prometheus(registry, "server"))(testRoutes)
  * }}}
  *
  * Analogously, the following code would wrap a [[org.http4s.client.Client]] with a [[org.http4s.client.middleware.Metrics]]
  * that records metrics to a given metric registry, classifying the metrics by HTTP method.
  * {{{
  * import org.http4s.client.middleware.Metrics
  * import org.http4s.client.prometheus.Prometheus
  *
  * val classifierFunc = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
  * val meteredClient = Metrics(Prometheus(registry, "client"), classifierFunc)(client)
  * }}}
  *
  * Registers the following metrics:
  *
  * {prefix}_response_duration_seconds{labels=classifier,method,phase} - Histogram
  *
  * {prefix}_active_request_count{labels=classifier} - Gauge
  *
  * {prefix}_request_count{labels=classifier,method,status} - Counter
  *
  * {prefix}_abnormal_terminations{labels=classifier,termination_type} - Histogram
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

  /**
    * Creates a  [[MetricsOps]] that supports Prometheus metrics
    * *
    * * @param registry a metrics collector registry
    * * @param prefix a prefix that will be added to all metrics
    **/
  def apply[F[_]](registry: CollectorRegistry, prefix: String = "org_http4s_server")(
      implicit F: Sync[F]): F[MetricsOps[F]] = F.delay {
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
          .labels(label(classifier), reportMethod(method), Phase.report(Phase.Headers))
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))

      }

      override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]): F[Unit] =
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
          classifier: Option[String]): F[Unit] = terminationType match {
        case Abnormal => recordAbnormal(elapsed, classifier)
        case Error => recordError(elapsed, classifier)
        case Timeout => recordTimeout(elapsed, classifier)
      }

      private def recordAbnormal(elapsed: Long, classifier: Option[String]): F[Unit] = F.delay {
        metrics.abnormalTerminations
          .labels(label(classifier), AbnormalTermination.report(AbnormalTermination.Abnormal))
          .observe(SimpleTimer.elapsedSecondsFromNanos(0, elapsed))
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
        MetricsCollection(
          responseDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames("classifier", "method", "phase")
            .register(registry),
          activeRequests = Gauge
            .build()
            .name(prefix + "_" + "active_request_count")
            .help("Total Active Requests.")
            .labelNames("classifier")
            .register(registry),
          requests = Counter
            .build()
            .name(prefix + "_" + "request_count")
            .help("Total Requests.")
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
}

case class MetricsCollection(
    responseDuration: Histogram,
    activeRequests: Gauge,
    requests: Counter,
    abnormalTerminations: Histogram
)

private sealed trait Phase
private object Phase {
  case object Headers extends Phase
  case object Body extends Phase
  def report(s: Phase): String = s match {
    case Headers => "headers"
    case Body => "body"
  }
}

private sealed trait AbnormalTermination
private object AbnormalTermination {
  case object Abnormal extends AbnormalTermination
  case object Error extends AbnormalTermination
  case object Timeout extends AbnormalTermination
  def report(t: AbnormalTermination): String = t match {
    case Abnormal => "abnormal"
    case Timeout => "timeout"
    case Error => "error"
  }
}
