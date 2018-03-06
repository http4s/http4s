package org.http4s.server.prometheus

import java.io.StringWriter

import cats.effect._
import cats.implicits._
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot._
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl

case class PrometheusExportService[F[_]](
    service: HttpService[F],
    cr: CollectorRegistry
)

object PrometheusExportService {

  def apply[F[_]](implicit F: Sync[F]): F[PrometheusExportService[F]] =
    for {
      cr <- F.delay(new CollectorRegistry())
      _ <- addDefaults(cr)(F)
    } yield PrometheusExportService[F](service(cr), cr)

  def service[F[_]](collectorRegistry: CollectorRegistry)(implicit F: Sync[F]): HttpService[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._

    HttpService[F] {
      case GET -> Root / "metrics" =>
        F.delay {
            val writer = new StringWriter
            TextFormat.write004(writer, collectorRegistry.metricFamilySamples)
            writer.toString
          }
          .flatMap(Ok(_))
    }
  }

  def addDefaults[F[_]](cr: CollectorRegistry)(implicit F: Sync[F]): F[Unit] =
    for {
      _ <- F.delay(cr.register(new StandardExports()))
      _ <- F.delay(cr.register(new MemoryPoolsExports()))
      _ <- F.delay(cr.register(new GarbageCollectorExports()))
      _ <- F.delay(cr.register(new ThreadExports()))
      _ <- F.delay(cr.register(new ClassLoadingExports()))
      result <- F.delay(cr.register(new VersionInfoExports()))
    } yield result

}
