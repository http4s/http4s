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

package org.http4s.ember.core.h2

import org.http4s.Http4sSuite
import scodec.bits.ByteVector

trait H2Suite extends Http4sSuite {
   protected def decodeFrames(bv: ByteVector): Vector[H2Frame] = {
    @annotation.tailrec
    def go(rest: ByteVector, acc: Vector[H2Frame]): Vector[H2Frame] =
      H2Frame.RawFrame.fromByteVector(rest) match {
        case Some((raw, tail)) =>
          H2Frame.fromRaw(raw) match {
            case Right(frame) => go(tail, acc :+ frame)
            case Left(_) => acc
          }
        case None => acc
      }
    go(bv, Vector.empty)
  }
}
