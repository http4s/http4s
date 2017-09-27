package org.http4s
package server

import cats._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

package object metrics {
  private val defaultMapper = {
    val module = new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, true)
    new ObjectMapper().registerModule(module)
  }

  /** Encodes a metric registry in JSON format */
  def metricRegistryEncoder[F[_]: Applicative](
      mapper: ObjectMapper = defaultMapper): EntityEncoder[F, MetricRegistry] =
    EntityEncoder[F, String].contramap { metricRegistry =>
      val writer = mapper.writerWithDefaultPrettyPrinter()
      writer.writeValueAsString(metricRegistry)
    }

  /** Returns an OK response with a JSON dump of a MetricRegistry */
  def metricsResponse[F[_]: Monad](
      registry: MetricRegistry,
      mapper: ObjectMapper = defaultMapper): F[Response[F]] = {
    implicit val encoder = metricRegistryEncoder[F](mapper)
    Response[F](Status.Ok).withBody(registry)
  }

  /** Returns an OK response with a JSON dump of a MetricRegistry */
  def metricsService[F[_]: Monad](
      registry: MetricRegistry,
      mapper: ObjectMapper = defaultMapper): HttpService[F] =
    HttpService {
      case req if req.method == Method.GET => metricsResponse(registry, mapper)
    }
}
