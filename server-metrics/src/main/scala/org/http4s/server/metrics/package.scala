package org.http4s
package server

import java.util.concurrent.TimeUnit
import scalaz.concurrent.Task

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.ObjectMapper

package object metrics {
  private val defaultMapper = {
    val module = new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, true)
    new ObjectMapper().registerModule(module)
  }

  /** Encodes a metric registry in JSON format */
  def metricRegistryEncoder(mapper: ObjectMapper = defaultMapper): EntityEncoder[MetricRegistry] =
    EntityEncoder[String].contramap { metricRegistry =>
      val writer = mapper.writerWithDefaultPrettyPrinter()
      writer.writeValueAsString(metricRegistry)
    }

  /** Returns an OK response with a JSON dump of a MetricRegistry */
  def metricsResponse(registry: MetricRegistry, mapper: ObjectMapper = defaultMapper): Task[Response] =
    Response(Status.Ok).withBody(registry)(metricRegistryEncoder(mapper))

  /** Returns an OK response with a JSON dump of a MetricRegistry */
  def metricsService(registry: MetricRegistry, mapper: ObjectMapper = defaultMapper): HttpService =
    HttpService.lift { req =>
      if (req.method == Method.GET) metricsResponse(registry, mapper)
      else Response.fallthrough
    }
}
