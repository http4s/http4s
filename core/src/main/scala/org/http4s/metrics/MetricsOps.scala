package org.http4s.metrics

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
    * @param method TODO
    * @param elapsed the time to record
    * @param classifier the request classifier
    */
  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Records the time to fully consume the response, including the body
    *
    * @param method TODO
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the request classifier
    */
  def recordTotalTime(method: Method, status: Status, elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Record abnormal terminations, like errors or timeouts
    *
    * @param elapsed the time to record
    * @param terminationType the type of termination
    * @param classifier the classifier to use
    */
  def recordAbnormalTermination(elapsed: Long, terminationType: TerminationType, classifier: Option[String]): F[Unit]
}

sealed trait TerminationType

object TerminationType {
  case object Abnormal extends TerminationType
  case object Error extends TerminationType
  case object Timeout extends TerminationType
}