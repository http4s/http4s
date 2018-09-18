package org.http4s.client.metrics.dropwizard

import cats.effect.Sync
import com.codahale.metrics.MetricRegistry
import java.util.concurrent.TimeUnit
import org.http4s.Status
import org.http4s.client.metrics.core.{MetricsOps, MetricsOpsFactory}

class DropwizardOps[F[_]](registry: MetricRegistry, prefix: String)(implicit F: Sync[F])
    extends MetricsOps[F] {

  override def increaseActiveRequests(destination: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, destination)}.active-requests").inc()
  }

  override def decreaseActiveRequests(destination: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, destination)}.active-requests").dec()
  }

  override def registerRequestHeadersTime(
      status: Status,
      elapsed: Long,
      destination: Option[String]): F[Unit] = F.delay {
    registry
      .timer(s"${namespace(prefix, destination)}.requests.headers")
      .update(elapsed, TimeUnit.NANOSECONDS)
  }

  override def registerRequestTotalTime(
      status: Status,
      elapsed: Long,
      destination: Option[String]): F[Unit] = F.delay {
    registry
      .timer(s"${namespace(prefix, destination)}.requests.total")
      .update(elapsed, TimeUnit.NANOSECONDS)

    registerStatusCode(status, destination)
  }

  override def increaseErrors(destination: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, destination)}.errors").inc()
  }

  override def increaseTimeouts(destination: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, destination)}.timeouts").inc()
  }

  private def namespace(prefix: String, destination: Option[String]): String =
    destination.map(d => s"${prefix}.${d}").getOrElse(s"${prefix}.default")

  private def registerStatusCode(status: Status, destination: Option[String]) =
    status.code match {
      case hundreds if hundreds < 200 =>
        registry.counter(s"${namespace(prefix, destination)}.1xx-responses").inc()
      case twohundreds if twohundreds < 300 =>
        registry.counter(s"${namespace(prefix, destination)}.2xx-responses").inc()
      case threehundreds if threehundreds < 400 =>
        registry.counter(s"${namespace(prefix, destination)}.3xx-responses").inc()
      case fourhundreds if fourhundreds < 500 =>
        registry.counter(s"${namespace(prefix, destination)}.4xx-responses").inc()
      case _ => registry.counter(s"${namespace(prefix, destination)}.5xx-responses").inc()
    }
}

class CodaHaleOpsFactory extends MetricsOpsFactory[MetricRegistry] {

  override def instance[F[_]: Sync](registry: MetricRegistry, prefix: String): MetricsOps[F] =
    new DropwizardOps[F](registry, prefix)

}

object DropwizardOps {
  implicit def codaHaleMetricsFactory: MetricsOpsFactory[MetricRegistry] = new CodaHaleOpsFactory()
}
