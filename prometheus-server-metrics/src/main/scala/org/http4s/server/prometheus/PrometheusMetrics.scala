package org.http4s.server.prometheus

import cats.Monoid
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

  import FilteringRule._
  private def metricsService[F[_]: Sync](
      serviceMetrics: ServiceMetrics,
      service: HttpService[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
      requestFiltering: RequestFiltering[F],
      responseFiltering: ResponseFiltering[F]
  )(
      req: Request[F]
  ): OptionT[F, Response[F]] = OptionT {
    for {
      initialTime <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(
        requestFiltering(req).fold(record = serviceMetrics.activeRequests.inc(), reject = ())
      )
      responseAtt <- service(req).value.attempt
      headersElapsed <- Sync[F].delay(System.nanoTime())
      result <- responseAtt.fold(
        e =>
          onServiceError(
            req,
            initialTime,
            headersElapsed,
            serviceMetrics,
            errorResponseHandler(e),
            requestFiltering,
            responseFiltering) *>
            Sync[F].raiseError[Option[Response[F]]](e),
        _.fold(
          onEmpty[F](
            req,
            initialTime,
            headersElapsed,
            serviceMetrics,
            emptyResponseHandler,
            requestFiltering,
            responseFiltering)
            .as(Option.empty[Response[F]])
        )(
          onResponse(
            req,
            initialTime,
            headersElapsed,
            serviceMetrics,
            requestFiltering,
            responseFiltering)(_).some
            .pure[F]
        )
      )
    } yield result
  }

  private def onEmpty[F[_]: Sync](
      request: Request[F],
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      emptyResponseHandler: Option[Status],
      requestFiltering: RequestFiltering[F],
      responseFiltering: ResponseFiltering[F]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      m = request.method
      _ <- emptyResponseHandler.traverse_(status =>
        Sync[F].delay {
          val response = Response[F](status = status)
          (requestFiltering(request) |+|
            responseFiltering(response)).fold(
            record = {
              serviceMetrics.requestDuration
                .labels(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase))
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

              serviceMetrics.requestDuration
                .labels(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase))
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

              serviceMetrics.requestCounter
                .labels(reportMethod(m), reportStatus(status))
                .inc()
            },
            reject = ()
          )
      })
      _ <- Sync[F].delay(
        requestFiltering(request).fold(record = serviceMetrics.activeRequests.dec(), reject = ())
      )
    } yield ()

  private def onResponse[F[_]: Sync](
      request: Request[F],
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      requestFiltering: RequestFiltering[F],
      responseFiltering: ResponseFiltering[F]
  )(
      response: Response[F]
  ): Response[F] = {
    val newBody = response.body
      .onFinalize {
        Sync[F].delay {
          val now = System.nanoTime
          val m = request.method
          (requestFiltering(request) |+| responseFiltering(response)).fold(
            record = {
              serviceMetrics.requestDuration
                .labels(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase))
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

              serviceMetrics.requestDuration
                .labels(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase))
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

              val responseStatus = response.status
              serviceMetrics.requestCounter
                .labels(reportMethod(m), reportStatus(responseStatus))
                .inc()

              serviceMetrics.activeRequests.dec()
            },
            reject = ()
          )
        }
      }
      .handleErrorWith(e =>
        Stream.eval(Sync[F].delay {
          serviceMetrics.abnormalTerminations.labels(
            AbnormalTermination.report(AbnormalTermination.Abnormal))
        }) *> Stream.raiseError[Byte](e).covary[F])
    response.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](
      request: Request[F],
      start: Long,
      headerTime: Long,
      serviceMetrics: ServiceMetrics,
      errorResponseHandler: Option[Status],
      requestFiltering: RequestFiltering[F],
      responseFiltering: ResponseFiltering[F]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      m = request.method
      _ <- errorResponseHandler.traverse_(status =>
        Sync[F].delay {

          val response = Response[F](status = status)

          (requestFiltering(request) |+| responseFiltering(response)).fold(
            record = {
              serviceMetrics.requestDuration
                .labels(reportMethod(m), ServingPhase.report(ServingPhase.HeaderPhase))
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, headerTime))

              serviceMetrics.requestDuration
                .labels(reportMethod(m), ServingPhase.report(ServingPhase.BodyPhase))
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, now))

              serviceMetrics.requestCounter
                .labels(reportMethod(m), reportStatus(status))
                .inc()

              serviceMetrics.abnormalTerminations
                .labels(AbnormalTermination.report(AbnormalTermination.ServerError))
                .inc()
            },
            reject = ()
          )
      })
      _ <- Sync[F].delay {
        requestFiltering(request).fold(
          record = serviceMetrics.activeRequests.dec(),
          reject = ()
        )

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
    withFiltering(c, prefix, emptyResponseHandler, errorResponseHandler)

  sealed trait FilteringRule
  case object Record extends FilteringRule
  case object Reject extends FilteringRule

  object FilteringRule {
    implicit class FilteringRulOps(f: FilteringRule) {
      def fold[A](record: => A, reject: => A): A = f match {
        case Record => record
        case Reject => reject
      }
    }

    implicit val filteringRuleMonoid: Monoid[FilteringRule] = new Monoid[FilteringRule] {
      override def empty: FilteringRule = Record

      override def combine(lhs: FilteringRule, rhs: FilteringRule): FilteringRule =
        (lhs, rhs) match {
          case (Reject, _) => Reject
          case (_, Reject) => Reject
          case (Record, Record) => Record
        }
    }
  }

  type RequestFiltering[F[_]] = Request[F] => FilteringRule
  type ResponseFiltering[F[_]] = Response[F] => FilteringRule

  /**
    * Same behavior as apply except lets you filter out http status codes from the response metrics.
    * For example keeping http status 503 from being included in the 5xx response metrics.
    * Note that by filtering out response metrics by status codes the response metrics will no longer match the request metrics one to one.
    */
  def withFiltering[F[_]: Sync](
      c: CollectorRegistry,
      prefix: String = "org_http4s_server",
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
      requestFiltering: RequestFiltering[F] = Function.const[FilteringRule, Request[F]](Record)(_:Request[F]),
      responseFiltering: ResponseFiltering[F] =
        Function.const[FilteringRule, Response[F]](Record)(_:Response[F])
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
          requestFiltering,
          responseFiltering)(_)
      )
    }
  }

}
