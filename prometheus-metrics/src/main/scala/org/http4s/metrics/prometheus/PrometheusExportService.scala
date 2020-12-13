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

package org.http4s.metrics.prometheus

import cats.effect._
import cats.syntax.all._
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot._
import java.io.StringWriter

import org.http4s.Uri.Path
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax

/*
 * PrometheusExportService Contains an HttpService
 * ready to be scraped by Prometheus, paired
 * with the CollectorRegistry that it is creating
 * metrics for, allowing custom metric registration.
 */
final class PrometheusExportService[F[_]: Sync] private (
    val routes: HttpRoutes[F],
    val collectorRegistry: CollectorRegistry
)

object PrometheusExportService {

  private val metricsPath: Path = path"/metrics"

  def apply[F[_]: Sync](collectorRegistry: CollectorRegistry): PrometheusExportService[F] =
    new PrometheusExportService(service(collectorRegistry), collectorRegistry)

  def build[F[_]: Sync]: Resource[F, PrometheusExportService[F]] =
    for {
      cr <- Prometheus.collectorRegistry[F]
      _ <- addDefaults(cr)
    } yield new PrometheusExportService[F](service(cr), cr)

  def generateResponse[F[_]: Sync](collectorRegistry: CollectorRegistry): F[Response[F]] =
    Sync[F]
      .delay {
        val writer = new StringWriter
        TextFormat.write004(writer, collectorRegistry.metricFamilySamples)
        writer.toString
      }
      .map(Response[F](Status.Ok).withEntity(_))

  def service[F[_]: Sync](collectorRegistry: CollectorRegistry): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req if req.method == Method.GET && req.pathInfo == metricsPath =>
        generateResponse(collectorRegistry)
    }

  def addDefaults[F[_]: Sync](cr: CollectorRegistry): Resource[F, Unit] =
    for {
      _ <- Prometheus.registerCollector(new StandardExports(), cr)
      _ <- Prometheus.registerCollector(new MemoryPoolsExports(), cr)
      _ <- Prometheus.registerCollector(new BufferPoolsExports(), cr)
      _ <- Prometheus.registerCollector(new GarbageCollectorExports(), cr)
      _ <- Prometheus.registerCollector(new ThreadExports(), cr)
      _ <- Prometheus.registerCollector(new ClassLoadingExports(), cr)
      _ <- Prometheus.registerCollector(new VersionInfoExports(), cr)
      _ <- Prometheus.registerCollector(new MemoryAllocationExports(), cr)
    } yield ()
}
