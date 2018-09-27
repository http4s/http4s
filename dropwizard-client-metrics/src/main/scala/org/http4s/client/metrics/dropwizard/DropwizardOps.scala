package org.http4s.client.metrics.dropwizard

import cats.effect.Sync
import com.codahale.metrics.MetricRegistry
import java.util.concurrent.TimeUnit
import org.http4s.Status
import org.http4s.client.metrics.core.{MetricsOps, MetricsOpsFactory}

/**
  * Implementation of [[MetricsOps]] that supports Dropwizard metrics
  *
  * @param registry a dropwizard metric registry
  * @param prefix a prefix that will be added to all metrics
  */
class DropwizardOps[F[_]](registry: MetricRegistry, prefix: String)(implicit F: Sync[F])
    extends MetricsOps[F] {

  override def increaseActiveRequests(classifier: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, classifier)}.active-requests").inc()
  }

  override def decreaseActiveRequests(classifier: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, classifier)}.active-requests").dec()
  }

  override def recordHeadersTime(
      status: Status,
      elapsed: Long,
      classifier: Option[String]): F[Unit] = F.delay {
    registry
      .timer(s"${namespace(prefix, classifier)}.requests.headers")
      .update(elapsed, TimeUnit.NANOSECONDS)
  }

  override def recordTotalTime(status: Status, elapsed: Long, classifier: Option[String]): F[Unit] =
    F.delay {
      registry
        .timer(s"${namespace(prefix, classifier)}.requests.total")
        .update(elapsed, TimeUnit.NANOSECONDS)

      registerStatusCode(status, classifier)
    }

  override def increaseErrors(classifier: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, classifier)}.errors").inc()
  }

  override def increaseTimeouts(classifier: Option[String]): F[Unit] = F.delay {
    registry.counter(s"${namespace(prefix, classifier)}.timeouts").inc()
  }

  private def namespace(prefix: String, classifier: Option[String]): String =
    classifier.map(d => s"${prefix}.${d}").getOrElse(s"${prefix}.default")

  private def registerStatusCode(status: Status, classifier: Option[String]) =
    status.code match {
      case hundreds if hundreds < 200 =>
        registry.counter(s"${namespace(prefix, classifier)}.1xx-responses").inc()
      case twohundreds if twohundreds < 300 =>
        registry.counter(s"${namespace(prefix, classifier)}.2xx-responses").inc()
      case threehundreds if threehundreds < 400 =>
        registry.counter(s"${namespace(prefix, classifier)}.3xx-responses").inc()
      case fourhundreds if fourhundreds < 500 =>
        registry.counter(s"${namespace(prefix, classifier)}.4xx-responses").inc()
      case _ => registry.counter(s"${namespace(prefix, classifier)}.5xx-responses").inc()
    }
}

class DropwizardOpsFactory extends MetricsOpsFactory[MetricRegistry] {

  override def instance[F[_]: Sync](registry: MetricRegistry, prefix: String): MetricsOps[F] =
    new DropwizardOps[F](registry, prefix)
}

trait DropwizardOpsFactoryInstances {

  implicit val dropwizardOpsFactory: MetricsOpsFactory[MetricRegistry] = new DropwizardOpsFactory()
}
