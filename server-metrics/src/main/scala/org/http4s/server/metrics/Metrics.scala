package org.http4s
package server
package metrics

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import fs2._
import java.util.concurrent.TimeUnit

object Metrics {

  def apply[F[_]](m: MetricRegistry, prefix: String = "org.http4s.server")(
      implicit F: Effect[F]): HttpMiddleware[F] = { service =>
    val active_requests = m.counter(s"${prefix}.active-requests")

    val abnormal_terminations = m.timer(s"${prefix}.abnormal-terminations")
    val service_errors = m.timer(s"${prefix}.service-errors")
    val headers_times = m.timer(s"${prefix}.headers-times")

    val responseTimers = ResponseTimers(
      m.timer(s"${prefix}.1xx-responses"),
      m.timer(s"${prefix}.2xx-responses"),
      m.timer(s"${prefix}.3xx-responses"),
      m.timer(s"${prefix}.4xx-responses"),
      m.timer(s"${prefix}.5xx-responses")
    )

    val get_req = m.timer(s"${prefix}.get-requests")
    val post_req = m.timer(s"${prefix}.post-requests")
    val put_req = m.timer(s"${prefix}.put-requests")
    val head_req = m.timer(s"${prefix}.head-requests")
    val move_req = m.timer(s"${prefix}.move-requests")
    val options_req = m.timer(s"${prefix}.options-requests")

    val trace_req = m.timer(s"${prefix}.trace-requests")
    val connect_req = m.timer(s"${prefix}.connect-requests")
    val delete_req = m.timer(s"${prefix}.delete-requests")
    val other_req = m.timer(s"${prefix}.other-requests")
    val total_req = m.timer(s"${prefix}.requests")

    val requestTimers = RequestTimers(
      get_req,
      post_req,
      put_req,
      head_req,
      move_req,
      options_req,
      trace_req,
      connect_req,
      delete_req,
      other_req,
      total_req
    )

    def generalMetrics(m: Method, elapsed: Long): F[Unit] = requestMetrics[F](
      requestTimers,
      active_requests
    )(m, elapsed)

    def onFinish(method: Method, start: Long)(
        r: Either[Throwable, Option[Response[F]]]): Either[Throwable, Option[Response[F]]] = {
      val elapsed = System.nanoTime() - start

      r.map { r =>
          headers_times.update(System.nanoTime() - start, TimeUnit.NANOSECONDS)
          val code = r.fold(Status.NotFound)(_.status)

          def capture(body: EntityBody[F]): EntityBody[F] =
            body
              .onFinalize {
                generalMetrics(method, elapsed) *>
                responseMetrics(responseTimers, code, elapsed)
              }
              .handleErrorWith { cause =>
                abnormal_terminations.update(elapsed, TimeUnit.NANOSECONDS)
                Stream.raiseError(cause)
              }
          r.map(resp => resp.copy(body = capture(resp.body)))
        }
        .leftMap { e =>
          generalMetrics(method, elapsed)
          responseTimers.resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
          service_errors.update(elapsed, TimeUnit.NANOSECONDS)
          e
        }
    }

    Kleisli { req =>

      for {
        now <- OptionT.liftF[F, Long](Sync[F].delay(System.nanoTime()))
        increment <- OptionT.liftF(Sync[F].delay(active_requests.inc()))
        out <- OptionT {
          service(req).value.attempt
            .flatMap(onFinish(req.method, now)(_)
              .fold[F[Option[Response[F]]]](
                F.raiseError,
                _.fold(handleUnmatched(active_requests))(handleMatched)
              )
            )
        }
      } yield out
    }
  }

  private def handleUnmatched[F[_]: Sync](c: Counter): F[Option[Response[F]]] =
    Sync[F].delay(c.dec()).as(Option.empty[Response[F]])
  private def handleMatched[F[_]: Sync](resp: Response[F]): F[Option[Response[F]]] = resp.some.pure[F]



  def responseTimer(responseTimers: ResponseTimers, status: Status): Timer = {
    status.code match {
      case hundreds if hundreds < 200 => responseTimers.resp1xx
      case twohundreds if twohundreds < 300 => responseTimers.resp2xx
      case threehundreds if threehundreds < 400 => responseTimers.resp3xx
      case fourhundreds if fourhundreds < 500 => responseTimers.resp4xx
      case _ => responseTimers.resp5xx
    }
  }

  def responseMetrics[F[_]: Sync](responseTimers: ResponseTimers, s: Status, elapsed: Long): F[Unit] =
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


}
