package org.http4s.server.prometheus

import io.prometheus.client._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s._
import org.http4s.server.HttpMiddleware

object PrometheusMetrics {

  /**
  * org_http4s_response_duration_seconds{labels=method,serving_phase} - Histogram
  *
  * org_http4s_active_request_total - Gauge 
  *
  * org_http4s_response_total{labels=method,code} - Counter
  * 
  * org_http4s_abnormal_terminations_total{labels=termination_type} - Counter
  **/

  private def reportRequestMethod(m: Method): String = m match {
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

  private def reportResponseCode(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

  private case class ServiceMetrics(
    requestDuration: Histogram,
    activeRequests: Gauge,
    responseCount: Counter,
    abnormalTerminations: Counter
  )

  private def metricsService[F[_]: Sync](
      serviceMetrics: ServiceMetrics,
      service: HttpService[F]
  )(
      req: Request[F]
  ): OptionT[F, Response[F]] = OptionT {
    for {
      initialTime <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(serviceMetrics.activeRequests.inc())
      responseAtt <- service(req).value.attempt
      headersElapsed <- Sync[F].delay(System.nanoTime())
      result <- responseAtt.fold(
        e => onServiceError(req.method, initialTime, serviceMetrics) *> Sync[F].raiseError[Option[Response[F]]](e),
        _.fold(
          onEmpty[F](req.method, initialTime, serviceMetrics).as(Option.empty[Response[F]])
        )(
          onResponse(req.method, initialTime, serviceMetrics)(_).some.pure[F]
        )
      )
    } yield result
  }

  private def onEmpty[F[_]: Sync](m: Method, start: Long, headerTime: Long, serviceMetrics: ServiceMetrics): F[Unit] = ???

  private def onResponse[F[_]: Sync](
    m: Method, 
    start: Long, 
    serviceMetrics: ServiceMetrics
  )(
    r: Response[F]
  ): Response[F] = {
    val newBody = r.body
      .onFinalize {
        for {
          finalBodyTime <- Sync[F].delay(System.nanoTime)
          _ <- requestMetrics(
            serviceMetrics.requestHist,
            serviceMetrics.generalMetrics.totalRequests,
            serviceMetrics.generalMetrics.activeRequests
          )(m, start, finalBodyTime)
          _ <- responseMetrics(
            serviceMetrics.responseHist,
            r.status,
            start,
            finalBodyTime
          )
        } yield ()
      }
      .handleErrorWith(e =>
        for {
          terminationTime <- Stream.eval(Sync[F].delay(System.nanoTime))
          _ <- Stream.eval(
            Sync[F].delay(
              serviceMetrics.generalMetrics.abnormalTerminations
                .observe(SimpleTimer.elapsedSecondsFromNanos(start, terminationTime))
            ))
          result <- Stream.raiseError[Byte](e).covary[F]
        } yield result)
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](m: Method, begin: Long, sm: ServiceMetrics): F[Unit] =
    for {
      end <- Sync[F].delay(System.nanoTime)
      _ <- requestMetrics(
        sm.requestHist,
        sm.generalMetrics.totalRequests,
        sm.generalMetrics.activeRequests
      )(m, begin, end)
      _ <- Sync[F].delay(
        sm.generalMetrics.serviceErrors.observe(SimpleTimer.elapsedSecondsFromNanos(begin, end)))
    } yield ()

  def apply[F[_]: Effect](
      c: CollectorRegistry,
      prefix: String = "org_http4s_server",
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = e => Status.InternalServerError.some
      ): F[HttpMiddleware[F]] = Sync[F].delay {
    
    val serviceMetrics =
      ServiceMetrics(
        requestDuration = Histogram
        .build()
        .name(prefix + "_" + "requests")
        .help("Request Duration.")
        .labelNames("method", "code")
        .register(c),
        activeRequests = Gauge
        .build()
        .name(prefix + "_" + "active_requests")
        .help("Total Active Requests.")
        .register(c),
        responseCount = ???,
        abnormalTerminations = ???
      )

    { service: HttpService[F] =>
      Kleisli(metricsService[F](serviceMetrics, service)(_))
    }
  }

}
