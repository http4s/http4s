/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.ApplicativeThrow
import cats.syntax.all._

/** Malformed bytes observed by an independent wire decoder, never adapter or transport errors. */
sealed class WireDecodingFailure(message: String) extends AssertionError(message)

private[wire] object WireDecodingFailure {
  final class Truncated(val boundary: WireReadFailure)
      extends WireDecodingFailure(boundary.getMessage)

  def read[F[_]: ApplicativeThrow, A](value: F[A]): F[A] =
    value.handleErrorWith {
      case error: WireReadFailure => ApplicativeThrow[F].raiseError(new Truncated(error))
      case error => ApplicativeThrow[F].raiseError(error)
    }
}

/** The bounded observer could not finish; this establishes no protocol violation. */
sealed class WireObservationLimit(message: String)
    extends IllegalStateException(s"$message; observation incomplete, scenario not fully exercised")

/** EOF at a requested framing boundary, distinct from invalid arguments and transport failures. */
private[wire] sealed abstract class WireReadFailure(message: String)
    extends IllegalArgumentException(message)

private[wire] object WireReadFailure {
  final case class Exact(needed: Int, received: Long)
      extends WireReadFailure(s"unexpected EOF: needed $needed bytes, received $received")
  final case class Delimiter(received: Long)
      extends WireReadFailure("unexpected EOF before delimiter")
}

private[wire] final case class WireBodyLimit(maxBytes: Int)
    extends WireObservationLimit(s"body exceeds $maxBytes bytes")
