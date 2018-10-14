package org.http4s.metrics

import org.http4s.{Method, Status}

/**
  * Describes an algebra capable of writing metrics to a metrics registry
  */
trait MetricsOps[F[_]] {

  /**
    * Increases the count of active requests
    *
    * @param classifier the classifier to apply
    */
  def increaseActiveRequests(classifier: Option[String]): F[Unit]

  /**
    * Decreases the count of active requests
    *
    * @param classifier the classifier to apply
    */
  def decreaseActiveRequests(classifier: Option[String]): F[Unit]

  /**
    * Records the time to receive the response headers
    *
    * @param method the http method of the request
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    */
  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): F[Unit]

  /**
    * Records the time to fully consume the response, including the body
    *
    * @param method the http method of the request
    * @param status the http status code of the response
    * @param elapsed the time to record
    * @param classifier the classifier to apply
    */
  def recordTotalTime(
      method: Method,
      status: Status,
      elapsed: Long,
      classifier: Option[String]): F[Unit]

  /**
    * Record abnormal terminations, like errors, timeouts or just other abnormal terminations.
    *
    * @param elapsed the time to record
    * @param terminationType the type of termination
    * @param classifier the classifier to apply
    */
  def recordAbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
      classifier: Option[String]): F[Unit]
}

/** Describes the type of abnormal termination*/
sealed trait TerminationType

object TerminationType {

  /** Signals just a generic abnormal termination */
  case object Abnormal extends TerminationType

  /** Signals an abnormal termination due to an error processing the request, either at the server or client side */
  case object Error extends TerminationType

  /** Signals a client timing out during a request */
  case object Timeout extends TerminationType
}
