package org.http4s.client.metrics.core

import cats.effect.Sync
import org.http4s.Status

/**
  * Describes an algebra capable of writing metrics to a metrics registry
  *
  */
trait MetricsOps[F[_]] {

  /**
    * Increases the count of active requests
    *
    * @param classifier the request classifier
    */
  def increaseActiveRequests(classifier: Option[String]): F[Unit]

  /**
    * Decreases the count of active requests
    *
    * @param classifier the request classifier
    */
  def decreaseActiveRequests(classifier: Option[String]): F[Unit]

  /**
    * Records the time to receive the response headers
    *
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the request classifier
    * @return
    */
  def recordHeadersTime(status: Status, elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Records the time to fully consume the response, including the body
    *
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the request classifier
    * @return
    */
  def recordTotalTime(status: Status, elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Increases the count of errors, excluding timeouts
    *
    * @param classifier the classifier to use
    */
  def increaseErrors(classifier: Option[String]): F[Unit]

  /**
    * Increases the count of timeouts
    *
    * @param classifier the classifier to use
    */
  def increaseTimeouts(classifier: Option[String]): F[Unit]
}

/**
  * Type class that allows to create a [[MetricsOps]] object for certain metrics registry R
  * @tparam R the specific metrics registry to use
  */
trait MetricsOpsFactory[R] {

  /**
    * Creates a new instance of [[MetricsOps]] for a specific metrics registry R
    *
    * @param registry the metrics registry where metrics should be recorded
    * @param prefix a prefix that will added to all metrics
    * @return the desired [[MetricsOps]]
    */
  def instance[F[_]: Sync](registry: R, prefix: String): MetricsOps[F]
}
