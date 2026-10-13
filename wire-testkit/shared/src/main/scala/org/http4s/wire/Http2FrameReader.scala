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

import cats.MonadThrow
import cats.syntax.all._

/** Bounded frame traversal with explicit observation, completion and control policies. */
private[wire] object Http2FrameReader {
  def readUntil[F[_]: MonadThrow](read: F[Http2Wire.Frame], limit: Int, detail: String)(
      observe: Http2Wire.Frame => F[Unit],
      accept: Http2Wire.Frame => Boolean,
      otherwise: Http2Wire.Frame => F[Unit],
  ): F[Http2Wire.Frame] =
    MonadThrow[F].tailRecM(limit) { remaining =>
      if (remaining <= 0) MonadThrow[F].raiseError(new WireObservationLimit(detail))
      else
        read.flatMap { frame =>
          observe(frame) *> (if (accept(frame)) frame.asRight[Int].pure[F]
                             else otherwise(frame).as((remaining - 1).asLeft[Http2Wire.Frame]))
        }
    }
}
