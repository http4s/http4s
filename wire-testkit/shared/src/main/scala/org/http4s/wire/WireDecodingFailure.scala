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
final class WireDecodingFailure(message: String) extends AssertionError(message)

private[wire] object WireDecodingFailure {
  // RawPeer reports exact-read/delimiter EOF with these messages. Translate only those
  // framing boundaries at decoder read sites; invalid arguments and arbitrary I/O escape.
  def read[F[_]: ApplicativeThrow, A](value: F[A]): F[A] =
    value.handleErrorWith {
      case error: IllegalArgumentException
          if error.getMessage == "unexpected EOF before delimiter" ||
            Option(error.getMessage).exists(
              _.matches("unexpected EOF: needed [0-9]+ bytes, received [0-9]+")
            ) =>
        ApplicativeThrow[F].raiseError(new WireDecodingFailure(error.getMessage))
      case error => ApplicativeThrow[F].raiseError(error)
    }
}

/** The bounded observer could not finish; this establishes no protocol violation. */
final class WireObservationLimit(message: String)
    extends IllegalStateException(s"$message; observation incomplete, scenario not fully exercised")
