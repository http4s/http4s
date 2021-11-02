/*
 * Copyright 2018 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package metrics

import cats.Applicative
import cats.effect.Sync
import cats.syntax.applicative._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.ObjectMapper

import java.util.concurrent.TimeUnit

package object dropwizard {
  private val defaultMapper = {
    val module = new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, true)
    new ObjectMapper().registerModule(module)
  }

  /** Encodes a metric registry in JSON format */
  def metricRegistryEncoder[F[_]](
      mapper: ObjectMapper = defaultMapper): EntityEncoder[F, MetricRegistry] =
    EntityEncoder[F, String].contramap { metricRegistry =>
      val writer = mapper.writerWithDefaultPrettyPrinter()
      writer.writeValueAsString(metricRegistry)
    }

  /** Returns an OK response with a JSON dump of a MetricRegistry */
  def metricsResponse[F[_]: Applicative](
      registry: MetricRegistry,
      mapper: ObjectMapper = defaultMapper): F[Response[F]] = {
    implicit val encoder: EntityEncoder[F, MetricRegistry] = metricRegistryEncoder[F](mapper)
    Response[F](Status.Ok).withEntity[MetricRegistry](registry).pure[F]
  }

  /** Returns an OK response with a JSON dump of a MetricRegistry */
  def metricsService[F[_]: Sync](
      registry: MetricRegistry,
      mapper: ObjectMapper = defaultMapper): HttpRoutes[F] =
    HttpRoutes.of {
      case req if req.method == Method.GET => metricsResponse(registry, mapper)
    }
}
