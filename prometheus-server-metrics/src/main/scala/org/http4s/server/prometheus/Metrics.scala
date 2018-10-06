package org.http4s.server.prometheus

import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s._

object Metrics {

  private def metricsService[F[_]: Sync](
      ops: MetricsOps[F],
      routes: HttpRoutes[F],
      emptyResponseHandler: Option[Status],
      errorResponseHandler: Throwable => Option[Status]
  )(
      req: Request[F]
  ): OptionT[F, Response[F]] = OptionT {
    for {
      initialTime <- Sync[F].delay(System.nanoTime())
      _ <- ops.increaseActiveRequests()
      responseAtt <- routes(req).value.attempt
      headersElapsed <- Sync[F].delay(System.nanoTime())
      result <- responseAtt.fold(
        e =>
          onServiceError(
            req.method,
            initialTime,
            headersElapsed,
            ops,
            errorResponseHandler(e)) *>
            Sync[F].raiseError[Option[Response[F]]](e),
        _.fold(
          onEmpty[F](req.method, initialTime, headersElapsed, ops, emptyResponseHandler)
            .as(Option.empty[Response[F]])
        )(
          onResponse(req.method, initialTime, headersElapsed, ops)(_).some.pure[F]
        )
      )
    } yield result
  }

  private def onEmpty[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime) // TODO: Convert to Clock
      _ <- emptyResponseHandler.traverse_(status =>
          ops.recordHeadersTime(method, headerTime - start) *>
          ops.recordTotalTime(method, now - start) *>
          ops.increaseRequests(method, status)
      )
      _ <- ops.decreaseActiveRequests()
    } yield ()

  private def onResponse[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F]
  )(
      r: Response[F]
  ): Response[F] = {
    val newBody = r.body
      .onFinalize {
          val now = System.nanoTime
          ops.recordHeadersTime(method, headerTime - start) *>
          ops.recordTotalTime(method, now - start) *>
          ops.increaseRequests(method, r.status) *>
          ops.decreaseActiveRequests()
      }
      .handleErrorWith(e => Stream.eval(ops.increaseAbnormalTerminations()) *> Stream.raiseError[F](e))
    r.copy(body = newBody)
  }

  private def onServiceError[F[_]: Sync](
      method: Method,
      start: Long,
      headerTime: Long,
      ops: MetricsOps[F],
      errorResponseHandler: Option[Status]
  ): F[Unit] =
    for {
      now <- Sync[F].delay(System.nanoTime)
      _ <- errorResponseHandler.traverse_(status =>
          ops.recordHeadersTime(method, headerTime - start) *>
          ops.recordTotalTime(method, now - start) *>
          ops.increaseRequests(method, status) *>
          ops.increaseErrors()
      )
      _ <- ops.decreaseActiveRequests()
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
      ops: MetricsOps[F],
      emptyResponseHandler: Option[Status] = Status.NotFound.some,
      errorResponseHandler: Throwable => Option[Status] = _ => Status.InternalServerError.some
  ): Kleisli[F, HttpRoutes[F], HttpRoutes[F]] = Kleisli { routes: HttpRoutes[F] =>
    Sync[F].delay {
      Kleisli(
        metricsService[F](ops, routes, emptyResponseHandler, errorResponseHandler)(_)
      )
    }
  }

}
