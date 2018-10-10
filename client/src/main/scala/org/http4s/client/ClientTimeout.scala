package org.http4s.client

import scala.concurrent.TimeoutException
import scala.concurrent.duration.Duration

sealed abstract class ClientTimeoutException extends TimeoutException {
  def timeout: Duration
}

final case class RequestTimeoutException(timeout: Duration) extends ClientTimeoutException
final case class ResponseHeaderTimeoutException(timeout: Duration) extends ClientTimeoutException
final case class IdleTimeoutException(timeout: Duration) extends ClientTimeoutException
