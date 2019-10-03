package org.http4s
package server
package metrics

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import fs2._
import java.util.concurrent.TimeUnit

object Metrics {

  def apply[F[_]](m: MetricRegistry, prefix: String = "org.http4s.server")(
      implicit F: Effect[F]): HttpMiddleware[F] = withFiltering[F](m, Set.empty, prefix)

  def withFiltering[F[_]](
      m: MetricRegistry,
      statusesToIgnore: Set[Status],
      prefix: String = "org.http4s.server")(implicit F: Effect[F]): HttpMiddleware[F] = { service =>
    val generalServiceMetrics = GeneralServiceMetrics(
      activeRequests = m.counter(s"${prefix}.active-requests"),
      abnormalTerminations = m.timer(s"${prefix}.abnormal-terminations"),
      serviceErrors = m.timer(s"${prefix}.service-errors"),
      headersTimes = m.timer(s"${prefix}.headers-times")
    )
    val responseTimers = ResponseTimers(
      resp1xx = m.timer(s"${prefix}.1xx-responses"),
      resp2xx = m.timer(s"${prefix}.2xx-responses"),
      resp3xx = m.timer(s"${prefix}.3xx-responses"),
      resp4xx = m.timer(s"${prefix}.4xx-responses"),
      resp5xx = m.timer(s"${prefix}.5xx-responses")
    )
    val requestTimers = RequestTimers(
      getReq = m.timer(s"${prefix}.get-requests"),
      postReq = m.timer(s"${prefix}.post-requests"),
      putReq = m.timer(s"${prefix}.put-requests"),
      headReq = m.timer(s"${prefix}.head-requests"),
      moveReq = m.timer(s"${prefix}.move-requests"),
      optionsReq = m.timer(s"${prefix}.options-requests"),
      traceReq = m.timer(s"${prefix}.trace-requests"),
      connectReq = m.timer(s"${prefix}.connect-requests"),
      deleteReq = m.timer(s"${prefix}.delete-requests"),
      otherReq = m.timer(s"${prefix}.other-requests"),
      totalReq = m.timer(s"${prefix}.requests")
    )

    val serviceMetrics = ServiceMetrics(generalServiceMetrics, requestTimers, responseTimers)

    Kleisli(metricsService[F](serviceMetrics, statusesToIgnore, service)(_))

  }

  private def metricsService[F[_]: Sync](
      serviceMetrics: ServiceMetrics,
      statusesToIgnore: Set[Status],
      service: HttpService[F])(req: Request[F]): OptionT[F, Response[F]] = OptionT {
    for {
      now <- Sync[F].delay(System.nanoTime())
      _ <- Sync[F].delay(serviceMetrics.generalMetrics.activeRequests.inc())
      e <- service(req).value.attempt
      resp <- metricsServiceHandler(req.method, now, serviceMetrics, statusesToIgnore, e)
    } yield resp
  }

  private def metricsServiceHandler[F[_]: Sync](
      method: Method,
      start: Long,
      serviceMetrics: ServiceMetrics,
      statusesToIgnore: Set[Status],
      e: Either[Throwable, Option[Response[F]]]): F[Option[Response[F]]] = {
    for {
      elapsed <- EitherT.liftF[F, Throwable, Long](Sync[F].delay(System.nanoTime() - start))
      respOpt <- EitherT(
        e.bitraverse[F, Throwable, Option[Response[F]]](
          manageServiceErrors(method, elapsed, serviceMetrics).as(_),
          _.map(manageResponse(method, start, elapsed, serviceMetrics, statusesToIgnore)).pure[F]
        ))
    } yield respOpt
  }.fold(
      Sync[F].raiseError[Option[Response[F]]],
      _.fold(handleUnmatched(serviceMetrics))(handleMatched)
    )
    .flatten

  private def manageResponse[F[_]: Sync](
      m: Method,
      start: Long,
      elapsedInit: Long,
      serviceMetrics: ServiceMetrics,
      statusesToIgnore: Set[Status]
  )(response: Response[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          elapsed <- Sync[F].delay(System.nanoTime() - start)
          _ <- incrementCounts(serviceMetrics.generalMetrics.headersTimes, elapsedInit)
          _ <- requestMetrics(
            serviceMetrics.requestTimers,
            serviceMetrics.generalMetrics.activeRequests)(m, elapsed)
          _ <- responseMetrics(
            serviceMetrics.responseTimers,
            response.status,
            elapsed,
            statusesToIgnore)
        } yield ()
      }
      .handleErrorWith(
        e =>
          Stream.eval(
            incrementCounts(serviceMetrics.generalMetrics.abnormalTerminations, elapsedInit)) *>
            Stream.raiseError[Byte](e))
    response.copy(body = newBody)
  }

  private def manageServiceErrors[F[_]: Sync](
      m: Method,
      elapsed: Long,
      serviceMetrics: ServiceMetrics): F[Unit] =
    requestMetrics(serviceMetrics.requestTimers, serviceMetrics.generalMetrics.activeRequests)(
      m,
      elapsed) *>
      incrementCounts(serviceMetrics.generalMetrics.serviceErrors, elapsed)

  private def handleUnmatched[F[_]: Sync](serviceMetrics: ServiceMetrics): F[Option[Response[F]]] =
    Sync[F].delay(serviceMetrics.generalMetrics.activeRequests.dec()).as(Option.empty[Response[F]])

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] =
    resp.some.pure[F]

  private def responseTimer(responseTimers: ResponseTimers, status: Status): Timer =
    status.code match {
      case hundreds if hundreds < 200 => responseTimers.resp1xx
      case twohundreds if twohundreds < 300 => responseTimers.resp2xx
      case threehundreds if threehundreds < 400 => responseTimers.resp3xx
      case fourhundreds if fourhundreds < 500 => responseTimers.resp4xx
      case _ => responseTimers.resp5xx
    }

  private def responseMetrics[F[_]: Sync](
      responseTimers: ResponseTimers,
      s: Status,
      elapsed: Long,
      statusesToIgnore: Set[Status]): F[Unit] =
    if (statusesToIgnore.contains(s)) {
      Sync[F].unit
    } else {
      incrementCounts(responseTimer(responseTimers, s), elapsed)
    }

  private def incrementCounts[F[_]: Sync](timer: Timer, elapsed: Long): F[Unit] =
    Sync[F].delay(timer.update(elapsed, TimeUnit.NANOSECONDS))

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

  private def requestMetrics[F[_]: Sync](
      rt: RequestTimers,
      active_requests: Counter
  )(method: Method, elapsed: Long): F[Unit] = {
    val timer = requestTimer(rt, method)
    incrementCounts(timer, elapsed) *>
      incrementCounts(rt.totalReq, elapsed) *>
      Sync[F].delay(active_requests.dec())
  }

  private case class RequestTimers(
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

  private case class ResponseTimers(
      resp1xx: Timer,
      resp2xx: Timer,
      resp3xx: Timer,
      resp4xx: Timer,
      resp5xx: Timer
  )

  private case class GeneralServiceMetrics(
      activeRequests: Counter,
      abnormalTerminations: Timer,
      serviceErrors: Timer,
      headersTimes: Timer
  )

  private case class ServiceMetrics(
      generalMetrics: GeneralServiceMetrics,
      requestTimers: RequestTimers,
      responseTimers: ResponseTimers
  )

}
