package org.http4s.metrics.prometheus

import cats.effect._
import cats.implicits._
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot._
import java.io.StringWriter
import org.http4s._
import org.http4s.dsl.Http4sDsl

/*
 * PromethusExportService Contains an HttpService
 * ready to be scraped by Prometheus, paired
 * with the CollectorRegistry that it is creating
 * metrics for, allowing custom metric registration.
 */
final class PrometheusExportService[F[_]: Sync] private (
    val routes: HttpRoutes[F],
    val collectorRegistry: CollectorRegistry
)

object PrometheusExportService {

  def apply[F[_]: Sync](collectorRegistry: CollectorRegistry): PrometheusExportService[F] =
    new PrometheusExportService(service(collectorRegistry), collectorRegistry)

  def build[F[_]: Sync]: F[PrometheusExportService[F]] =
    for {
      cr <- new CollectorRegistry().pure[F]
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

  def service[F[_]: Sync](collectorRegistry: CollectorRegistry): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => generateResponse(collectorRegistry)
    }
  }

  def addDefaults[F[_]: Sync](cr: CollectorRegistry): F[Unit] = Sync[F].delay {
    cr.register(new StandardExports())
    cr.register(new MemoryPoolsExports())
    cr.register(new BufferPoolsExports())
    cr.register(new GarbageCollectorExports())
    cr.register(new ThreadExports())
    cr.register(new ClassLoadingExports())
    cr.register(new VersionInfoExports())
    cr.register(new MemoryAllocationExports())
  }

}
