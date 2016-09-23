package org.http4s
package server
package metrics

import java.util.concurrent.TimeUnit

import scalaz._
import scalaz.stream.Cause._
import scalaz.stream.Process._
import scalaz.concurrent.Task
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

    def onFinish(method: Method, start: Long)(r: Throwable \/ Response): Throwable \/ Response = {
      val elapsed = System.nanoTime() - start

      r match {
        case \/-(r) =>
          headers_times.update(System.nanoTime() - start, TimeUnit.NANOSECONDS)
          val code = r.status.code

          val body = r.body.onHalt { cause =>
            val elapsed = System.nanoTime() - start

            generalMetrics(method, elapsed)

            if (code < 200) resp1xx.update(elapsed, TimeUnit.NANOSECONDS)
            else if (code < 300) resp2xx.update(elapsed, TimeUnit.NANOSECONDS)
            else if (code < 400) resp3xx.update(elapsed, TimeUnit.NANOSECONDS)
            else if (code < 500) resp4xx.update(elapsed, TimeUnit.NANOSECONDS)
            else resp5xx.update(elapsed, TimeUnit.NANOSECONDS)

            cause match {
              case End | Kill =>
              case Error(_) =>
                abnormal_termination.update(elapsed, TimeUnit.NANOSECONDS)
            }
            Halt(cause)
          }

          \/-(r.copy(body = body))

       case e@ -\/(_)       =>
          generalMetrics(method, elapsed)
          resp5xx.update(elapsed, TimeUnit.NANOSECONDS)
          service_failure.update(elapsed, TimeUnit.NANOSECONDS)
          e
      }
    }

    Service.lift { req: Request =>
      val now = System.nanoTime()
      active_requests.inc()
      new Task(service(req).get.map(onFinish(req.method, now)))
    }
  }
}
