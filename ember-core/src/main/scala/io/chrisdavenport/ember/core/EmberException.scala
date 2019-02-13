package org.http4s.ember.core

import cats.implicits._

sealed trait EmberException extends RuntimeException

object EmberException {
  final case class Timeout(started: Long, timedOut: Long) extends EmberException {
    override def getMessage: String = show"Timeout Occured - Started: $started, Timed Out: $timedOut"
  }
  final case class IncompleteClientRequest(missing: String) extends IllegalArgumentException with EmberException {
    override def getMessage: String = show"Incomplete Client Request: Mising $missing"
  }

  final case class ParseError(message: String) extends EmberException {
    override def getMessage: String = message
  }

  final case class ChunkedEncodingError(message: String) extends EmberException {
    override def getMessage: String = message
  }
  
}