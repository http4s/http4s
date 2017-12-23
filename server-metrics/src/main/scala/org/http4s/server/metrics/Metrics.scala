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
      implicit F: Effect[F]): HttpMiddleware[F] = { service =>

    val generalServiceMetrics = GeneralServiceMetrics(
      active_requests = m.counter(s"${prefix}.active-requests"),
      abnormal_terminations = m.timer(s"${prefix}.abnormal-terminations"),
      service_errors = m.timer(s"${prefix}.service-errors"),
      headers_times = m.timer(s"${prefix}.headers-times")
    )
    val responseTimers = ResponseTimers(
      resp1xx = m.timer(s"${prefix}.1xx-responses"),
      resp2xx = m.timer(s"${prefix}.2xx-responses"),
      resp3xx = m.timer(s"${prefix}.3xx-responses"),
      resp4xx = m.timer(s"${prefix}.4xx-responses"),
      resp5xx = m.timer(s"${prefix}.5xx-responses")
    )
    val requestTimers = RequestTimers(
      get_req = m.timer(s"${prefix}.get-requests"),
      post_req = m.timer(s"${prefix}.post-requests"),
      put_req = m.timer(s"${prefix}.put-requests"),
      head_req = m.timer(s"${prefix}.head-requests"),
      move_req = m.timer(s"${prefix}.move-requests"),
      options_req = m.timer(s"${prefix}.options-requests"),
      trace_req = m.timer(s"${prefix}.trace-requests"),
      connect_req = m.timer(s"${prefix}.connect-requests"),
      delete_req = m.timer(s"${prefix}.delete-requests"),
      other_req = m.timer(s"${prefix}.other-requests"),
      total_req = m.timer(s"${prefix}.requests")
    )

    val serviceMetrics = ServiceMetrics(generalServiceMetrics, requestTimers, responseTimers)

    Kleisli(metricsService[F](serviceMetrics, service)(_))
  }

  private def metricsService[F[_]: Sync](serviceMetrics: ServiceMetrics, service: HttpService[F])(req: Request[F]): OptionT[F, Response[F]] = {
    val method = req.method
    for {
      now <- OptionT.liftF[F, Long](Sync[F].delay(System.nanoTime()))
      _ <- OptionT.liftF[F, Unit](Sync[F].delay(serviceMetrics.generalMetrics.active_requests.inc()))
      resp <- OptionT{
        service(req)
          .value
          .attempt
          .flatMap(metricsServiceHandler(method, now, serviceMetrics, _))
      }
    } yield resp
  }

  private def metricsServiceHandler[F[_]: Sync](method: Method,
                                                start: Long,
                                                serviceMetrics: ServiceMetrics,
                                                e: Either[Throwable, Option[Response[F]]]
                                               ): F[Option[Response[F]]] = {
    for {
      elapsed <- EitherT.liftF[F, Throwable, Long](Sync[F].delay(System.nanoTime() - start))
      respOpt <- EitherT(e.bitraverse[F, Throwable, Option[Response[F]]](
        manageServiceErrors(method, elapsed, serviceMetrics).as(_),
        _.map(manageResponse(method, start, elapsed, serviceMetrics)).pure[F]
      ))
    } yield respOpt
  }.fold(
    Sync[F].raiseError[Option[Response[F]]],
    _.fold(handleUnmatched(serviceMetrics, start))(handleMatched)
  ).flatten

  private def manageResponse[F[_]: Sync](
                                   m: Method,
                                   start: Long,
                                   elapsedInit: Long,
                                   serviceMetrics: ServiceMetrics
                                 )(response: Response[F]): Response[F] = {
    val newBody = response.body
      .onFinalize {
        for {
          elapsed <- Sync[F].delay(System.nanoTime() - start)
          _ <- incrementCounts(serviceMetrics.generalMetrics.headers_times, elapsedInit)
          _ <- requestMetrics(serviceMetrics.requestTimers, serviceMetrics.generalMetrics.active_requests)(m, elapsed)
          _ <- responseMetrics(serviceMetrics.responseTimers, response.status, elapsed)
        } yield ()
      }
      .handleErrorWith(e =>
        Stream.eval(incrementCounts(serviceMetrics.generalMetrics.abnormal_terminations, elapsedInit)) *>
        Stream.raiseError[Byte](e)
      )
    response.copy(body = newBody)
  }

  private def manageServiceErrors[F[_]: Sync](m: Method, elapsed: Long, serviceMetrics: ServiceMetrics): F[Unit] =
    requestMetrics(serviceMetrics.requestTimers, serviceMetrics.generalMetrics.active_requests)(m, elapsed) *>
    incrementCounts(serviceMetrics.responseTimers.resp5xx, elapsed) *>
    incrementCounts(serviceMetrics.generalMetrics.service_errors, elapsed)


  private def handleUnmatched[F[_]: Sync](serviceMetrics: ServiceMetrics, start: Long): F[Option[Response[F]]] =
    for {
      elapsed <- Sync[F].delay(System.nanoTime() - start)
      _ <- incrementCounts(serviceMetrics.responseTimers.resp4xx, elapsed)
      _ <- Sync[F].delay(serviceMetrics.generalMetrics.active_requests.dec())
    } yield Option.empty[Response[F]]

  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] = resp.some.pure[F]

  private def responseTimer(responseTimers: ResponseTimers, status: Status): Timer = {
    status.code match {
      case hundreds if hundreds < 200 => responseTimers.resp1xx
      case twohundreds if twohundreds < 300 => responseTimers.resp2xx
      case threehundreds if threehundreds < 400 => responseTimers.resp3xx
      case fourhundreds if fourhundreds < 500 => responseTimers.resp4xx
      case _ => responseTimers.resp5xx
    }
  }

  private def responseMetrics[F[_]: Sync](responseTimers: ResponseTimers, s: Status, elapsed: Long): F[Unit] =
    incrementCounts(responseTimer(responseTimers, s), elapsed)

  private def incrementCounts[F[_]: Sync](timer: Timer, elapsed: Long): F[Unit] =
    Sync[F].delay(timer.update(elapsed, TimeUnit.NANOSECONDS))

  private def requestTimer[F[_]: Sync](rt: RequestTimers, method: Method): Timer = method match {
    case Method.GET => rt.get_req
    case Method.POST => rt.post_req
    case Method.PUT => rt.put_req
    case Method.HEAD => rt.head_req
    case Method.MOVE => rt.move_req
    case Method.OPTIONS => rt.options_req
    case Method.TRACE => rt.trace_req
    case Method.CONNECT => rt.connect_req
    case Method.DELETE => rt.delete_req
    case _ => rt.other_req
  }

  private def requestMetrics[F[_]: Sync](
                    rt: RequestTimers,
                    active_requests: Counter
                    )(method: Method, elapsed: Long): F[Unit] = {
    val timer = requestTimer(rt, method)
    incrementCounts(timer, elapsed) *>
    incrementCounts(rt.total_req, elapsed) *>
    Sync[F].delay(active_requests.dec())
  }


  private case class RequestTimers(
                            get_req: Timer,
                            post_req: Timer,
                            put_req: Timer,
                            head_req: Timer,
                            move_req: Timer,
                            options_req: Timer,
                            trace_req: Timer,
                            connect_req: Timer,
                            delete_req: Timer,
                            other_req: Timer,
                            total_req: Timer
                          )

  private case class ResponseTimers(
                             resp1xx: Timer,
                             resp2xx: Timer,
                             resp3xx: Timer,
                             resp4xx: Timer,
                             resp5xx: Timer
                           )

  private case class GeneralServiceMetrics(
                                          active_requests: Counter,
                                          abnormal_terminations: Timer,
                                          service_errors: Timer,
                                          headers_times: Timer
                                          )

  private case class ServiceMetrics(
                                   generalMetrics: GeneralServiceMetrics,
                                   requestTimers: RequestTimers,
                                   responseTimers: ResponseTimers
                                   )


}
