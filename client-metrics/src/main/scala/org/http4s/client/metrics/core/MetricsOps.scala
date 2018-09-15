package org.http4s.client.metrics.core

import org.http4s.Status

trait MetricsOps {

  def increaseActiveRequests(destination: Option[String]): Unit

  def decreaseActiveRequests(destination: Option[String]): Unit

  def registerRequestHeadersTime(status: Status, elapsed: Long, destination: Option[String]): Unit

  def registerRequestTotalTime(status: Status, elapsed: Long, destination: Option[String]): Unit

  def increaseErrors(destination: Option[String]): Unit

  def increaseTimeouts(destination: Option[String]): Unit

}

trait MetricsOpsFactory[R] {
  def instance(registry: R, prefix: String): MetricsOps
}
