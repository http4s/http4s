package org.http4s.server.prometheus

import io.prometheus.client._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s._

object PrometheusMetrics {

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

  private def reportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

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

  private case class ServiceMetrics(
      requestDuration: Histogram,
      activeRequests: Gauge,
      requestCounter: Counter,
      abnormalTerminations: Counter
  )

  private def metricsService[F[_]: Sync](
      serviceMetrics: ServiceMetrics,
      service: HttpService[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
      statusesToIgnore: Set[Status]
  )(
      req: Request[F]
  ): OptionT[F, Response[F]] = OptionT {
    for {
      initialTime <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(serviceMetrics.activeRequests.inc())
      responseAtt <- service(req).value.attempt
      headersElapsed <- Sync[F].delay(System.nanoTime())
      result <- responseAtt.fold(
        e =>
          onServiceError(
            req.method,
            initialTime,
            headersElapsed,
            serviceMetrics,
            errorResponseHandler(e),
            statusesToIgnore) *>
            Sync[F].raiseError[Option[Response[F]]](e),
        _.fold(
          onEmpty[F](
            req.method,
            initialTime,
            headersElapsed,
            serviceMetrics,
            emptyResponseHandler,
            statusesToIgnore)
            .as(Option.empty[Response[F]])
        )(
          onResponse(req.method, initialTime, headersElapsed, serviceMetrics, statusesToIgnore)(_).some
            .pure[F]
        )
      )
    } yield result
  }

  private def onEmpty[F[_]: Sync](
      m: Method,
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      emptyResponseHandler: Option[Status],
      statusesToIgnore: Set[Status]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      _ <- emptyResponseHandler.traverse_(status =>
        Sync[F].delay {
          serviceMetrics.requestDuration
            .labels(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase))
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

          serviceMetrics.requestDuration
            .labels(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase))
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

          if (!statusesToIgnore.contains(status)) {
            serviceMetrics.requestCounter
              .labels(reportMethod(m), reportStatus(status))
              .inc()
          }
      })
      _ <- Sync[F].delay(serviceMetrics.activeRequests.dec())
    } yield ()

  private def onResponse[F[_]: Sync](
      m: Method,
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      statusesToIgnore: Set[Status]
  )(
      r: Response[F]
  ): Response[F] = {
    val newBody = r.body
      .onFinalize {
        Sync[F].delay {
          val now = System.nanoTime
          serviceMetrics.requestDuration
            .labels(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase))
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

          serviceMetrics.requestDuration
            .labels(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase))
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

          val responseStatus = r.status
          if (!statusesToIgnore.contains(responseStatus)) {
            serviceMetrics.requestCounter
              .labels(reportMethod(m), reportStatus(responseStatus))
              .inc()
          }

          serviceMetrics.activeRequests.dec()
        }
      }
      .handleErrorWith(e =>
        Stream.eval(Sync[F].delay {
          serviceMetrics.abnormalTerminations.labels(
            AbnormalTermination.report(AbnormalTermination.Abnormal))
        }) *> Stream.raiseError[Byte](e).covary[F])
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](
      m: Method,
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      errorResponseHandler: Option[Status],
      statusesToIgnore: Set[Status]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      _ <- errorResponseHandler.traverse_(status =>
        Sync[F].delay {
          serviceMetrics.requestDuration
            .labels(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase))
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

          serviceMetrics.requestDuration
            .labels(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase))
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

          if (!statusesToIgnore.contains(status)) {
            serviceMetrics.requestCounter
              .labels(reportMethod(m), reportStatus(status))
              .inc()
          }

          serviceMetrics.abnormalTerminations
            .labels(AbnormalTermination.report(AbnormalTermination.ServerError))
            .inc()

      })
      _ <- Sync[F].delay {
        serviceMetrics.activeRequests.dec()
      }
    } yield ()

  /**
    * Metrics --
    *
    * org_http4s_response_duration_seconds{labels=method,serving_phase} - Histogram
    *
    * org_http4s_active_request_count - Gauge
    *
    * org_http4s_response_total{labels=method,code} - Counter
    *
    * org_http4s_abnormal_terminations_total{labels=termination_type} - Counter
    *
    * Labels --
    *
    * method: Enumeration
    * values: get, put, post, head, move, options, trace, connect, delete, other
    *
    * serving_phase: Enumeration
    * values: header_phase, body_phase
    *
    * code: Enumeration
    * values:  1xx, 2xx, 3xx, 4xx, 5xx
    *
    * termination_type: Enumeration
    * values: abnormal_termination, server_error
    *
    **/
  def apply[F[_]: Sync](
      c: CollectorRegistry,
      prefix: String = "org_http4s_server",
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some
  ): Kleisli[F, HttpService[F], HttpService[F]] =
    withFiltering(c, Set.empty, prefix, emptyResponseHandler, errorResponseHandler)

  def withFiltering[F[_]: Sync](
      c: CollectorRegistry,
      statusesToIgnore: Set[Status],
      prefix: String = "org_http4s_server",
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some
  ): Kleisli[F, HttpService[F], HttpService[F]] = Kleisli { service: HttpService[F] =>
    Sync[F].delay {
      val serviceMetrics =
        ServiceMetrics(
          requestDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames("method", "serving_phase")
            .register(c),
          activeRequests = Gauge
            .build()
            .name(prefix + "_" + "active_request_count")
            .help("Total Active Requests.")
            .register(c),
          requestCounter = Counter
            .build()
            .name(prefix + "_" + "response_total")
            .help("Total Responses.")
            .labelNames("method", "code")
            .register(c),
          abnormalTerminations = Counter
            .build()
            .name(prefix + "_" + "abnormal_terminations_total")
            .help("Total Abnormal Terminations.")
            .labelNames("termination_type")
            .register(c)
        )
      Kleisli(
        metricsService[F](
          serviceMetrics,
          service,
          emptyResponseHandler,
          errorResponseHandler,
          statusesToIgnore)(_)
      )
    }
  }

}
