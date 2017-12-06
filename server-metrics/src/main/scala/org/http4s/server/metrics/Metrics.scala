package org.http4s
package server
package metrics

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import com.codahale.metrics.MetricRegistry
import fs2._
import java.util.concurrent.TimeUnit

object Metrics {

  def apply[F[_]](m: MetricRegistry, prefix: String = "org.http4s.server")(
      implicit F: Effect[F]): HttpMiddleware[F] = { service =>
    val active_requests = m.counter(s"${prefix}.active-requests")

    val abnormal_termination = m.timer(s"${prefix}.abnormal-termination")
    val service_failure = m.timer(s"${prefix}.service-error")
    val headers_times = m.timer(s"${prefix}.headers-times")

    val resp1xx = m.timer(s"${prefix}.1xx-responses")
    val resp2xx = m.timer(s"${prefix}.2xx-responses")
    val resp3xx = m.timer(s"${prefix}.3xx-responses")
    val resp4xx = m.timer(s"${prefix}.4xx-responses")
    val resp5xx = m.timer(s"${prefix}.5xx-responses")

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

    def generalMetrics(method: Method, elapsed: Long): Unit = {
      method match {
        case Method.GET => get_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.POST => post_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.PUT => put_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.HEAD => head_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.MOVE => move_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.OPTIONS => options_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.TRACE => trace_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.CONNECT => connect_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.DELETE => delete_req.update(elapsed, TimeUnit.NANOSECONDS)
        case _ => other_req.update(elapsed, TimeUnit.NANOSECONDS)
      }

      total_req.update(elapsed, TimeUnit.NANOSECONDS)
      active_requests.dec()
    }

    def onFinish(method: Method, start: Long)(
        r: Either[Throwable, Option[Response[F]]]): Either[Throwable, Option[Response[F]]] = {
      val elapsed = System.nanoTime() - start

      r.map { r =>
          headers_times.update(System.nanoTime() - start, TimeUnit.NANOSECONDS)
          val code = r.fold(Status.NotFound)(_.status).code

          def capture(body: EntityBody[F]): EntityBody[F] =
            body
              .onFinalize {
                F.delay {
                  generalMetrics(method, elapsed)
                  if (code < 200) resp1xx.update(elapsed, TimeUnit.NANOSECONDS)
                  else if (code < 300) resp2xx.update(elapsed, TimeUnit.NANOSECONDS)
                  else if (code < 400) resp3xx.update(elapsed, TimeUnit.NANOSECONDS)
                  else if (code < 500) resp4xx.update(elapsed, TimeUnit.NANOSECONDS)
                  else resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
                }
              }
              .handleErrorWith { cause =>
                abnormal_termination.update(elapsed, TimeUnit.NANOSECONDS)
                Stream.raiseError(cause)
              }
          r.map(resp => resp.copy(body = capture(resp.body)))
        }
        .leftMap { e =>
          generalMetrics(method, elapsed)
          resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
          service_failure.update(elapsed, TimeUnit.NANOSECONDS)
          e
        }
    }

    Kleisli { req =>
      val now = System.nanoTime()
      active_requests.inc()
      OptionT {
        service(req).value.attempt
          .flatMap(onFinish(req.method, now)(_)
            .fold[F[Option[Response[F]]]](F.raiseError, F.pure))
      }
    }
  }
}
