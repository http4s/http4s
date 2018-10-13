package org.http4s.server.middleware

import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream
import java.util.concurrent.TimeUnit
import org.http4s._
import org.http4s.metrics.MetricsOps
import org.http4s.server.HttpMiddleware
import org.http4s.metrics.TerminationType.{Abnormal, Error}

object Metrics {

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
  def apply[F[_]](
    ops: MetricsOps[F],
    emptyResponseHandler: Option[Status] = Status.NotFound.some,
    errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some,
    classifierF: Request[F] => Option[String] = { _: Request[F] => None }
  )(implicit F: Effect[F], clock: Clock[F]): HttpMiddleware[F] = { routes: HttpRoutes[F] =>
    Kleisli(metricsService[F](ops, routes, emptyResponseHandler, errorResponseHandler, classifierF)(_))
  }

  private def metricsService[F[_]: Sync](
      ops: MetricsOps[F],
      routes: HttpRoutes[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status],
      classifierF: Request[F] => Option[String]
  )(req: Request[F])(implicit clock: Clock[F]): OptionT[F, Response[F]] = OptionT {
    for {
      initialTime <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- ops.increaseActiveRequests(classifierF(req))
      responseAtt <- routes(req).value.attempt
      headersElapsed <- clock.monotonic(TimeUnit.NANOSECONDS)
      result <- responseAtt.fold(
        e =>
          onServiceError(
            req.method,
            initialTime,
            headersElapsed,
            ops,
            errorResponseHandler(e),
            classifierF(req)) *>
            Sync[F].raiseError[Option[Response[F]]](e),
        _.fold(
          onEmpty[F](req.method, initialTime, headersElapsed, ops, emptyResponseHandler, classifierF(req))
            .as(Option.empty[Response[F]])
        )(
          onResponse(req.method, initialTime, headersElapsed, ops, classifierF(req))(_).some.pure[F]
        )
      )
    } yield result
  }

  private def onEmpty[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status],
      classifier: Option[String]
  )(implicit clock: Clock[F]): F[Unit] =
    for {
      now <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- emptyResponseHandler.traverse_(status =>
          ops.recordHeadersTime(method, headerTime - start, classifier) *>
          ops.recordTotalTime(method, status, now - start, classifier)
      )
      _ <- ops.decreaseActiveRequests(classifier)
    } yield ()

  private def onResponse[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      classifier: Option[String]
  )(r: Response[F])(implicit clock: Clock[F]): Response[F] = {
    val newBody = r.body
      .onFinalize {
        for {
          now <- clock.monotonic(TimeUnit.NANOSECONDS)
          _   <- ops.recordHeadersTime(method, headerTime - start, classifier)
          _   <- ops.recordTotalTime(method, r.status, now - start, classifier)
          _   <- ops.decreaseActiveRequests(classifier)
        } yield {}
      }
      .handleErrorWith(e =>
        for {
          now <- Stream.eval(clock.monotonic(TimeUnit.NANOSECONDS))
          _ <- Stream.eval(ops.recordAbnormalTermination(now - start, Abnormal, classifier))
          r <- Stream.raiseError[F](e)
        } yield r
      )
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      errorResponseHandler: Option[Status],
      classifier: Option[String]
  )(implicit clock: Clock[F]): F[Unit] =
    for {
      now <- clock.monotonic(TimeUnit.NANOSECONDS)
      _ <- errorResponseHandler.traverse_(status =>
          ops.recordHeadersTime(method, headerTime - start, classifier) *>
          ops.recordTotalTime(method, status, now - start, classifier) *>
          ops.recordAbnormalTermination(now - start, Error, classifier)
      )
      _ <- ops.decreaseActiveRequests(classifier)
    } yield ()
}