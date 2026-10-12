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

/** A failed incidental reply is never a rejection verdict. False requires the caller to
  * stop replying and independently observe bounded, validated transport closure.
  */
private[wire] object Http2RejectionWrite {
  def reply[F[_]: ApplicativeThrow](write: F[Unit]): F[Boolean] =
    write.as(true).handleErrorWith { error =>
      RawPeer.raiseAdapterOrTimeout[F](error) *>
        (if (RuntimeInfo.isClosedWrite(error)) false.pure[F]
         else ApplicativeThrow[F].raiseError[Boolean](error))
    }
}
