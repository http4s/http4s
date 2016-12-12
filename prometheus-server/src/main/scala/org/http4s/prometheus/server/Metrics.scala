package org.http4s
package prometheus
package server

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import io.prometheus.client._
import scalaz.stream.Cause._
import scalaz.{\/, -\/, \/-}
import scalaz.concurrent.Task
import scalaz.stream.Process.{Halt, halt}

object Metrics {
  def apply(
    prefix: String = "",
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    histogramBuckets: List[Double] = List(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10),
    whitelistedMethods: Set[Method] = DefaultMethods
  )(service: HttpService): HttpService = {

    def collectorName(prefix: String, name: String) =
      if (prefix.isEmpty()) name
      else s"${prefix}_${name}"

    val activeRequests =
      new Gauge.Builder()
        .name(Collector.sanitizeMetricName(collectorName(prefix, "http_requests_active")))
        .help("Currently active requests")
        .register(registry)

    val requestLatency =
      new Histogram.Builder()
        .name(Collector.sanitizeMetricName(collectorName(prefix, "http_request_time_seconds")))
        .help("Time spent servicing requests in seconds")
        .labelNames("method", "status")
        .buckets(histogramBuckets: _*)
        .register(registry)

    // Only support a handful of methods, or else requests can create
    // an arbitrary number of time series by creating arbitrary custom
    // methods.
    def methodLabel(method: Method) =
      if (whitelistedMethods.contains(method)) method.name
      else "other"

    def record(method: Method, status: Status, elapsed: Long) = {
      activeRequests.inc(-1.0)
      val statusLabel = (status.code / 100) + "xx"
      val histo = requestLatency.labels(methodLabel(method), statusLabel)
      histo.observe(elapsed / 1.0e9)
    }

    def onFinish(req: Request, start: Long)(r: Throwable \/ Response): Throwable \/ Response = {
      val elapsed = System.nanoTime - start
      r match {
        case \/-(resp) =>
          \/-(resp.copy(body = resp.body.onHalt { cause =>
            record(req.method, resp.status, elapsed)
            Halt(cause)
          }))

        case e @ -\/(_) =>
          record(req.method, Status.InternalServerError, elapsed)
          e
      }
    }

    Service.lift { req: Request =>
      val start = System.nanoTime
      activeRequests.inc()
      new Task(service(req).get.map(onFinish(req, start)))
    }
  }

  val DefaultMethods = Set(
    Method.GET,
    Method.POST,
    Method.PUT,
    Method.HEAD,
    Method.OPTIONS,
    Method.TRACE,
    Method.CONNECT,
    Method.DELETE,
    Method.PATCH
  )
}
