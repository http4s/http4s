package org.http4s.metrics

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