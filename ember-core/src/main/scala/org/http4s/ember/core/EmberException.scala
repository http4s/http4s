/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import cats.syntax.all._
import java.time.Instant

sealed trait EmberException extends RuntimeException with Product with Serializable

object EmberException {
  final case class Timeout(started: Instant, timedOut: Instant) extends EmberException {
    override def getMessage: String =
      show"Timeout Occured - Started: ${started.toString}, Timed Out: ${timedOut.toString}"
  }
  final case class IncompleteClientRequest(missing: String)
      extends IllegalArgumentException
      with EmberException {
    override def getMessage: String = show"Incomplete Client Request: Mising $missing"
  }

  final case class ParseError(message: String) extends EmberException {
    override def getMessage: String = message
  }

  final case class ChunkedEncodingError(message: String) extends EmberException {
    override def getMessage: String = message
  }
}
