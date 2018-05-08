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
      abnormalTerminations: Counter,
      tags : Map[String, String]
  )

  private def metricsService[F[_]: Sync](
      serviceMetrics: ServiceMetrics,
      routes: HttpRoutes[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status]
  )(
      req: Request[F]
  ): OptionT[F, Response[F]] = OptionT {
    for {
      initialTime <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(serviceMetrics.activeRequests.inc())
      responseAtt <- routes(req).value.attempt
      headersElapsed <- Sync[F].delay(System.nanoTime())
      result <- responseAtt.fold(
        e =>
          onServiceError(
            req.method,
            initialTime,
            headersElapsed,
            serviceMetrics,
            errorResponseHandler(e)) *>
            Sync[F].raiseError[Option[Response[F]]](e),
        _.fold(
          onEmpty[F](req.method, initialTime, headersElapsed, serviceMetrics, emptyResponseHandler)
            .as(Option.empty[Response[F]])
        )(
          onResponse(req.method, initialTime, headersElapsed, serviceMetrics)(_).some.pure[F]
        )
      )
    } yield result
  }

  private def onEmpty[F[_]: Sync](
      m: Method,
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      emptyResponseHandler: Option[Status]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      _ <- emptyResponseHandler.traverse_(status =>
        Sync[F].delay {
          serviceMetrics.requestDuration
            .labels(Seq(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase)) ++ serviceMetrics.tags.values.toSeq : _*)
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))
        
          serviceMetrics.requestDuration
            .labels(Seq(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase)) ++ serviceMetrics.tags.values.toSeq : _*)
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

          serviceMetrics.requestCounter
            .labels(Seq(reportMethod(m), reportStatus(status)) ++ serviceMetrics.tags.values.toSeq : _*)
            .inc()
          
      })
      _ <- Sync[F].delay{
          serviceMetrics.activeRequests.labels(serviceMetrics.tags.values.toSeq : _*).dec()
      }
    } yield ()

  private def onResponse[F[_]: Sync](
      m: Method,
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics
  )(
      r: Response[F]
  ): Response[F] = {
    val newBody = r.body
      .onFinalize {
        Sync[F].delay {
          val now = System.nanoTime
          serviceMetrics.requestDuration
            .labels(Seq(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase)) ++ serviceMetrics.tags.values.toSeq : _*)
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

          serviceMetrics.requestDuration
            .labels(Seq(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase)) ++ serviceMetrics.tags.values.toSeq : _*)
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

          serviceMetrics.requestCounter
            .labels(Seq(reportMethod(m), reportStatus(r.status)) ++ serviceMetrics.tags.values.toSeq : _*)
            .inc()

          serviceMetrics.activeRequests.labels(serviceMetrics.tags.values.toSeq : _*).dec()
        }
      }
      .handleErrorWith(e =>
        Stream.eval(Sync[F].delay {
          serviceMetrics.abnormalTerminations.labels(
            Seq(AbnormalTermination.report(AbnormalTermination.Abnormal)) ++ serviceMetrics.tags.values.toSeq : _*
          )
        }) *> Stream.raiseError[Byte](e).covary[F])
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](
      m: Method,
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      errorResponseHandler: Option[Status]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      _ <- errorResponseHandler.traverse_(status =>
        Sync[F].delay {
          serviceMetrics.requestDuration
            .labels(Seq(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase)) ++ serviceMetrics.tags.values.toSeq : _*)
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

          serviceMetrics.requestDuration
            .labels(Seq(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase)) ++ serviceMetrics.tags.values.toSeq : _*)
            .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

          serviceMetrics.requestCounter
            .labels(Seq(reportMethod(m), reportStatus(status)) ++ serviceMetrics.tags.values.toSeq : _*)
            .inc()

          serviceMetrics.abnormalTerminations
            .labels(Seq(AbnormalTermination.report(AbnormalTermination.ServerError)) ++ serviceMetrics.tags.values.toSeq : _*)
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
      tags: Map[String, String] = Map.empty[String, String],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = e => Status.InternalServerError.some
  ): Kleisli[F, HttpRoutes[F], HttpRoutes[F]] = Kleisli { routes: HttpRoutes[F] =>
    Sync[F].delay {
      val serviceMetrics: ServiceMetrics =
        ServiceMetrics(
          requestDuration = Histogram
            .build()
            .name(prefix + "_" + "response_duration_seconds")
            .help("Response Duration in seconds.")
            .labelNames(Seq("method", "serving_phase") ++ tags.keys.toSeq : _*)
            .register(c),
          activeRequests = Gauge
            .build()
            .name(prefix + "_" + "active_request_count")
            .help("Total Active Requests.")
            .labelNames(tags.keys.toSeq : _*)
            .register(c),
          requestCounter = Counter
            .build()
            .name(prefix + "_" + "response_total")
            .help("Total Responses.")
            .labelNames(Seq("method", "code") ++ tags.keys.toSeq : _*)
            .register(c),
          abnormalTerminations = Counter
            .build()
            .name(prefix + "_" + "abnormal_terminations_total")
            .help("Total Abnormal Terminations.")
            .labelNames(Seq("termination_type") ++ tags.keys.toSeq : _*)
            .register(c),
          tags = tags
        )
      Kleisli(
        metricsService[F](serviceMetrics, routes, emptyResponseHandler, errorResponseHandler)(_)
      )
    }
  }

}
