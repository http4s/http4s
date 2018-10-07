package org.http4s.server.metrics

import cats.effect.Sync
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import java.util.concurrent.TimeUnit
import org.http4s.{Method, Status}

object Dropwizard {

  /**
    * Creates a [[MetricsOps]] that supports Dropwizard metrics
    *
    * @param registry a dropwizard metric registry
    * @param prefix a prefix that will be added to all metrics
    */
  def apply[F[_]](registry: MetricRegistry, prefix: String)(implicit F: Sync[F]): MetricsOps[F] =
    new MetricsOps[F] {

      override def increaseActiveRequests(): F[Unit] = F.delay {
        generalServiceMetrics.activeRequests.inc()
      }

      override def decreaseActiveRequests(): F[Unit] = F.delay {
        generalServiceMetrics.activeRequests.dec()
      }

      override def increaseRequests(): F[Unit] = F.delay {
        requestTimers.totalReq.update(1, TimeUnit.NANOSECONDS)
      }

      override def recordHeadersTime(elapsed: Long): F[Unit] = F.delay {
        generalServiceMetrics.headersTimes.update(elapsed, TimeUnit.NANOSECONDS)
      }

      override def recordTotalTime(method: Method, status: Status, elapsed: Long): F[Unit] =
        F.delay {
          requestTimer(requestTimers, method).update(elapsed, TimeUnit.NANOSECONDS)
          registerStatusCode(status, elapsed)
        }

      override def recordTotalTime(method: Method, elapsed: Long): F[Unit] =
        F.delay {
          requestTimer(requestTimers, method).update(elapsed, TimeUnit.NANOSECONDS)
        }

      override def recordTotalTime(status: Status, elapsed: Long): F[Unit] =
        F.delay {
          registerStatusCode(status, elapsed)
        }

      override def increaseErrors(elapsed: Long): F[Unit] = F.delay {
        generalServiceMetrics.serviceErrors.update(elapsed, TimeUnit.NANOSECONDS)
      }

      override def increaseTimeouts(): F[Unit] = F.delay {
      }

      override def increaseAbnormalTerminations(elapsed: Long): F[Unit] = F.delay {
        generalServiceMetrics.abnormalTerminations.update(elapsed, TimeUnit.NANOSECONDS)
      }

      private def registerStatusCode(status: Status, elapsed: Long) =
        status.code match {
          case hundreds if hundreds < 200 =>
            responseTimers.resp1xx.update(elapsed, TimeUnit.NANOSECONDS)
          case twohundreds if twohundreds < 300 =>
            responseTimers.resp2xx.update(elapsed, TimeUnit.NANOSECONDS)
          case threehundreds if threehundreds < 400 =>
            responseTimers.resp3xx.update(elapsed, TimeUnit.NANOSECONDS)
          case fourhundreds if fourhundreds < 500 =>
            responseTimers.resp4xx.update(elapsed, TimeUnit.NANOSECONDS)
          case _ => responseTimers.resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
        }

      
      val generalServiceMetrics = GeneralServiceMetrics(
        activeRequests = registry.counter(s"${prefix}.active-requests"),
        abnormalTerminations = registry.timer(s"${prefix}.abnormal-terminations"),
        serviceErrors = registry.timer(s"${prefix}.service-errors"),
        headersTimes = registry.timer(s"${prefix}.headers-times")
      )
      val responseTimers = ResponseTimers(
        resp1xx = registry.timer(s"${prefix}.1xx-responses"),
        resp2xx = registry.timer(s"${prefix}.2xx-responses"),
        resp3xx = registry.timer(s"${prefix}.3xx-responses"),
        resp4xx = registry.timer(s"${prefix}.4xx-responses"),
        resp5xx = registry.timer(s"${prefix}.5xx-responses")
      )
      val requestTimers = RequestTimers(
        getReq = registry.timer(s"${prefix}.get-requests"),
        postReq = registry.timer(s"${prefix}.post-requests"),
        putReq = registry.timer(s"${prefix}.put-requests"),
        headReq = registry.timer(s"${prefix}.head-requests"),
        moveReq = registry.timer(s"${prefix}.move-requests"),
        optionsReq = registry.timer(s"${prefix}.options-requests"),
        traceReq = registry.timer(s"${prefix}.trace-requests"),
        connectReq = registry.timer(s"${prefix}.connect-requests"),
        deleteReq = registry.timer(s"${prefix}.delete-requests"),
        otherReq = registry.timer(s"${prefix}.other-requests"),
        totalReq = registry.timer(s"${prefix}.requests")
      )

    }

  private def requestTimer[F[_]: Sync](rt: RequestTimers, method: Method): Timer = method match {
    case Method.GET => rt.getReq
    case Method.POST => rt.postReq
    case Method.PUT => rt.putReq
    case Method.HEAD => rt.headReq
    case Method.MOVE => rt.moveReq
    case Method.OPTIONS => rt.optionsReq
    case Method.TRACE => rt.traceReq
    case Method.CONNECT => rt.connectReq
    case Method.DELETE => rt.deleteReq
    case _ => rt.otherReq
  }
  
  
}

private final case class RequestTimers(
  getReq: Timer,
  postReq: Timer,
  putReq: Timer,
  headReq: Timer,
  moveReq: Timer,
  optionsReq: Timer,
  traceReq: Timer,
  connectReq: Timer,
  deleteReq: Timer,
  otherReq: Timer,
  totalReq: Timer
)

private final case class ResponseTimers(
 resp1xx: Timer,
 resp2xx: Timer,
 resp3xx: Timer,
 resp4xx: Timer,
 resp5xx: Timer
)

private final case class GeneralServiceMetrics(
  activeRequests: Counter,
  abnormalTerminations: Timer,
  serviceErrors: Timer,
  headersTimes: Timer
)

private final case class ServiceMetrics(
 generalMetrics: GeneralServiceMetrics,
 requestTimers: RequestTimers,
 responseTimers: ResponseTimers
)
