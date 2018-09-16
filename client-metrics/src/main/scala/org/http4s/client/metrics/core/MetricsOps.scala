package org.http4s.client.metrics.core

import cats.effect.Sync
import org.http4s.Status

trait MetricsOps[F[_]] {

  def increaseActiveRequests(destination: Option[String]): F[Unit]

  def decreaseActiveRequests(destination: Option[String]): F[Unit]

  def registerRequestHeadersTime(
      status: Status,
      elapsed: Long,
      destination: Option[String]): F[Unit]

  def registerRequestTotalTime(status: Status, elapsed: Long, destination: Option[String]): F[Unit]

  def increaseErrors(destination: Option[String]): F[Unit]

  def increaseTimeouts(destination: Option[String]): F[Unit]

}

trait MetricsOpsFactory[R] {
  def instance[F[_]: Sync](registry: R, prefix: String): MetricsOps[F]
}
