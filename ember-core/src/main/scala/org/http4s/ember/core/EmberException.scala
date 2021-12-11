/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.core

import cats.syntax.all._

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.util.control.NoStackTrace

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

  final case class EmptyStream() extends EmberException {
    override def getMessage: String = "Cannot Parse Empty Stream"
  }

  final case class ReachedEndOfStream() extends EmberException {
    override def getMessage: String = "Reached End Of Stream While Reading"
  }

  final case class MessageTooLong(maxHeaderSize: Int) extends EmberException {
    override def getMessage: String = s"HTTP Header Section Exceeds Max Size: $maxHeaderSize Bytes"
  }

  final case class ReadTimeout(duration: Duration) extends EmberException {
    override def getMessage: String = s"Read timeout after $duration"
  }

  private[ember] final case class RequestHeadersTimeout(duration: Duration)
      extends EmberException
      with NoStackTrace {
    override def getMessage: String = s"Timed out waiting for request headers after $duration"
  }

}
