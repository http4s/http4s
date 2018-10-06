package org.http4s.server.prometheus

import org.http4s.{Method, Status}

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
  def increaseActiveRequests(classifier: Option[String] = None): F[Unit]

  /**
    * Decreases the count of active requests
    *
    * @param classifier the request classifier
    */
  def decreaseActiveRequests(classifier: Option[String] = None): F[Unit]

  // TODO
  def increaseRequests(method: Method, status: Status, classifier: Option[String] = None): F[Unit]

  /**
    * Records the time to receive the response headers
    *
    * @param method TODO
    * @param elapsed the time to record
    * @param classifier the request classifier
    * @return
    */
  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String] = None): F[Unit]

  /**
    * Records the time to fully consume the response, including the body
    *
    * @param method TODO
    * @param elapsed the time to record
    * @param classifier the request classifier
    * @return
    */
  def recordTotalTime(method: Method, elapsed: Long, classifier: Option[String] = None): F[Unit]

  /**
    * Increases the count of errors, excluding timeouts
    *
    * @param classifier the classifier to use
    */
  def increaseErrors(classifier: Option[String] = None): F[Unit]

  /**
    * Increases the count of timeouts
    *
    * @param classifier the classifier to use
    */
  def increaseTimeouts(classifier: Option[String] = None): F[Unit]

  /**
    * TODO
    */
  def increaseAbnormalTerminations(classifier: Option[String] = None): F[Unit]
}
