package org.http4s
package server
package middleware

import java.util.concurrent.TimeUnit

import com.codahale.metrics._

import org.http4s.{Method, Response, Request}

import scalaz.stream.Cause.End
import scalaz.{\/, -\/, \/-}
import scalaz.concurrent.Task
import scalaz.stream.Process.{Halt, halt}

object Metrics {

  def meter(m: MetricRegistry, name: String)(srvc: HttpService): HttpService = {

    val active_requests = m.counter(name + ".active-requests")

    val abnormal_termination = m.timer(name + ".abnormal-termination")
    val service_failure = m.timer(name + ".service-error")
    val headers_times = m.timer(name + ".headers-times")


    val resp1xx = m.timer(name + ".1xx-responses")
    val resp2xx = m.timer(name + ".2xx-responses")
    val resp3xx = m.timer(name + ".3xx-responses")
    val resp4xx = m.timer(name + ".4xx-responses")
    val resp5xx = m.timer(name + ".5xx-responses")
//    "org.eclipse.jetty.servlet.ServletContextHandler.async-dispatches"
//    "org.eclipse.jetty.servlet.ServletContextHandler.async-timeouts"

//    "http.connections"

//    "org.eclipse.jetty.servlet.ServletContextHandler.dispatches"
    val get_req = m.timer(name + ".get-requests")
    val post_req = m.timer(name + ".post-requests")
    val put_req = m.timer(name + ".put-requests")
    val head_req = m.timer(name + ".head-requests")
    val move_req = m.timer(name + ".move-requests")
    val options_req = m.timer(name + ".options-requests")

    val trace_req = m.timer(name + ".trace-requests")
    val connect_req = m.timer(name + ".connect-requests")
    val delete_req = m.timer(name + ".delete-requests")
    val other_req = m.timer(name + ".other-requests")
    val total_req = m.timer(name + ".requests")


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
              case End => halt
              case _   =>
                abnormal_termination.update(elapsed, TimeUnit.NANOSECONDS)
                Halt(cause)
            }
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
      new Task(srvc(req).get.map(onFinish(req.method, now)))
    }
  }
}
