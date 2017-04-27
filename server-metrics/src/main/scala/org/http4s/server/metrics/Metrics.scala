package org.http4s
package server
package metrics

import java.util.concurrent.TimeUnit

import cats.implicits._
import fs2.util.Attempt
import fs2.{Stream, Task}
import com.codahale.metrics.MetricRegistry

object Metrics {

  def apply(m: MetricRegistry, prefix: String = "org.http4s.server"): HttpMiddleware = { service =>
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
        case Method.GET     => get_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.POST    => post_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.PUT     => put_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.HEAD    => head_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.MOVE    => move_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.OPTIONS => options_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.TRACE   => trace_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.CONNECT => connect_req.update(elapsed, TimeUnit.NANOSECONDS)
        case Method.DELETE  => delete_req.update(elapsed, TimeUnit.NANOSECONDS)
        case _              => other_req.update(elapsed, TimeUnit.NANOSECONDS)
      }

      total_req.update(elapsed, TimeUnit.NANOSECONDS)
      active_requests.dec()
    }

    def onFinish(method: Method, start: Long)(r: Attempt[MaybeResponse]): Attempt[MaybeResponse] = {
      val elapsed = System.nanoTime() - start

      r.map { r =>
        headers_times.update(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        val code = r.cata(_.status, Status.NotFound).code

        def capture(r: Response) = r.body.onFinalize[Task] {
          Task.delay {
            generalMetrics(method, elapsed)
            if (code < 200) resp1xx.update(elapsed, TimeUnit.NANOSECONDS)
            else if (code < 300) resp2xx.update(elapsed, TimeUnit.NANOSECONDS)
            else if (code < 400) resp3xx.update(elapsed, TimeUnit.NANOSECONDS)
            else if (code < 500) resp4xx.update(elapsed, TimeUnit.NANOSECONDS)
            else resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
          }
        }.onError { cause =>
          abnormal_termination.update(elapsed, TimeUnit.NANOSECONDS)
          Stream.fail(cause)
        }
        r
      }.leftMap { e =>
        generalMetrics(method, elapsed)
        resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
        service_failure.update(elapsed, TimeUnit.NANOSECONDS)
        e
      }
    }

    Service.lift { req: Request =>
      val now = System.nanoTime()
      active_requests.inc()
      service(req).attempt.flatMap(onFinish(req.method, now)(_).fold(Task.fail, Task.now))
    }
  }
}
