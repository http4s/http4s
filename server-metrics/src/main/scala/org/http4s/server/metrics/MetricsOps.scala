package org.http4s.server.metrics

import org.http4s.{Method, Status}

/**
  * Describes an algebra capable of writing metrics to a metrics registry
  *
  */
trait MetricsOps[F[_]] {

  /**
    * Increases the count of active requests
    *
    */
  def increaseActiveRequests(classifier: Option[String]): F[Unit]

  /**
    * Decreases the count of active requests
    *
    */
  def decreaseActiveRequests(classifier: Option[String]): F[Unit]

  // TODO
  def increaseRequests(elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Records the time to receive the response headers
    *
    * @param elapsed the time to record
    * @return
    */
  def recordHeadersTime(elapsed: Long,classifier: Option[String]): F[Unit]

  /**
    * Records the time to fully consume the response, including the body
    *
    * @param method TODO
    *               @param status TODO
    * @param elapsed the time to record
    * @return
    */
  def recordTotalTime(method: Method, status: Status, elapsed: Long, classifier: Option[String]): F[Unit]
  def recordTotalTime(method: Method, elapsed: Long, classifier: Option[String]): F[Unit]
  def recordTotalTime(status: Status, elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Increases the count of errors, excluding timeouts
    *
    * @param elapsed TODO
    */
  def increaseErrors(elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Increases the count of timeouts
    *
    */
  def increaseTimeouts(classifier: Option[String]): F[Unit]

  /**
    * TODO
    */
  def increaseAbnormalTerminations(elapsed: Long, classifier: Option[String]): F[Unit]
}
