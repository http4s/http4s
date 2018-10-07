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
  def increaseActiveRequests(): F[Unit]

  /**
    * Decreases the count of active requests
    *
    */
  def decreaseActiveRequests(): F[Unit]

  // TODO
  def increaseRequests(): F[Unit]

  /**
    * Records the time to receive the response headers
    *
    * @param elapsed the time to record
    * @return
    */
  def recordHeadersTime(elapsed: Long): F[Unit]

  /**
    * Records the time to fully consume the response, including the body
    *
    * @param method TODO
    *               @param status TODO
    * @param elapsed the time to record
    * @return
    */
  def recordTotalTime(method: Method, status: Status, elapsed: Long): F[Unit]
  def recordTotalTime(method: Method, elapsed: Long): F[Unit]
  def recordTotalTime(status: Status, elapsed: Long): F[Unit]

  /**
    * Increases the count of errors, excluding timeouts
    *
    * @param elapsed TODO
    */
  def increaseErrors(elapsed: Long): F[Unit]

  /**
    * Increases the count of timeouts
    *
    */
  def increaseTimeouts(): F[Unit]

  /**
    * TODO
    */
  def increaseAbnormalTerminations(elapsed: Long): F[Unit]
}
